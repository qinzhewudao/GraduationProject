package perf.benchmark;

import core.adapt.Predicate;
import core.adapt.Predicate.PREDTYPE;
import core.adapt.Query;
import core.adapt.spark.SparkQuery;
import core.common.globals.Globals;
import core.common.globals.TableInfo;
import core.utils.ConfUtils;
import core.utils.HDFSUtils;
import core.utils.TypeUtils;
import core.utils.TypeUtils.TYPE;
import org.apache.hadoop.fs.FileSystem;

import java.util.ArrayList;
import java.util.List;

public class CMTWorkload {
	public ConfUtils cfg;

	public String schemaString;

	int numFields;

	int method;

    String tableName;

    TableInfo tableInfo;

	public void setUp() {
		tableName = "cmt";

		cfg = new ConfUtils(BenchmarkSettings.conf);
		FileSystem fs = HDFSUtils.getFSByHadoopHome(cfg.getHADOOP_HOME());

		// Load table info.
		Globals.loadTableInfo(tableName, cfg.getHDFS_WORKING_DIR(), fs);
		tableInfo = Globals.getTableInfo(tableName);
		assert tableInfo != null;
	}

	public Predicate getPredicate(String pred) {
		String[] parts = pred.split(" ");

		int attrId = tableInfo.schema.getAttributeId(parts[0].trim());
		
		if (attrId == -1) {
			throw new RuntimeException("Unknown attr: " + parts[0].trim());
		}
		
		TYPE attrType = tableInfo.schema.getType(attrId);
		Object value = TypeUtils.deserializeValue(attrType, parts[2].trim().replaceAll("'", ""));
		String predTypeStr = parts[1].trim();
		PREDTYPE predType;
		switch (predTypeStr) {
		case ">":
			predType = PREDTYPE.GT;
			break;
		case ">=":
			predType = PREDTYPE.GEQ;
			break;
		case "<":
			predType = PREDTYPE.LT;
			break;
		case "<=":
			predType = PREDTYPE.LEQ;
			break;
		case "=":
			predType = PREDTYPE.EQ;
			break;
		default:
			throw new RuntimeException("Unknown predType " + predTypeStr);
		}

		Predicate p = new Predicate(tableInfo, parts[0].trim(), attrType, value, predType);
		return p;
	}
	
	public List<Query> generateWorkload() {
		byte[] stringBytes = HDFSUtils.readFile(
				HDFSUtils.getFSByHadoopHome(cfg.getHADOOP_HOME()),
				"/user/mdindex/cmt_queries.log");
		String queriesString = new String(stringBytes);
		String[] queries = queriesString.split("\n");
		List<Query> ret = new ArrayList<Query>();
		for (int i=0; i<queries.length; i++) {
			String queryString = queries[i];
			String[] parts = queryString.split("\\|");
			String[] predicates = parts[1].split(";");
			List<Predicate> queryPreds = new ArrayList<Predicate>();
			for (int j=0; j<predicates.length; j++) {
				Predicate p = getPredicate(predicates[j]);
				List<Predicate> preds = p.getNormalizedPredicates();
				queryPreds.addAll(preds);
			}
			Predicate[] predArray = queryPreds.toArray(new Predicate[queryPreds.size()]);
			ret.add(new Query(tableName, predArray));
		}

		return ret;
	}

    public void testWorkload() {
		long start, end;
		SparkQuery sq = new SparkQuery(cfg);
		List<Query> queries = generateWorkload();
		for (Query q: queries) {
			System.out.println("INFO: Query:" + q.toString());
		}
	}

	public void runWorkload() {
		long start, end;
		SparkQuery sq = new SparkQuery(cfg);
		List<Query> queries = generateWorkload();
		for (Query q: queries) {
			System.out.println("INFO: Query:" + q.toString());
		}

		for (Query q : queries) {
			start = System.currentTimeMillis();
			long result = sq.createAdaptRDD(cfg.getHDFS_WORKING_DIR(),
					q).count();
			end = System.currentTimeMillis();
			System.out.println("RES: Time Taken: " + (end - start) +
					"; Result: " + result);
		}
	}

	public void loadSettings(String[] args) {
		int counter = 0;
		while (counter < args.length) {
			switch (args[counter]) {
			case "--schema":
				schemaString = args[counter + 1];
				counter += 2;
				break;
			case "--numFields":
				numFields = Integer.parseInt(args[counter + 1]);
				counter += 2;
				break;
			case "--method":
				method = Integer.parseInt(args[counter + 1]);
				counter += 2;
				break;
			default:
				// Something we don't use
				counter += 2;
				break;
			}
		}
	}

	public static void main(String[] args) {
		BenchmarkSettings.loadSettings(args);
		BenchmarkSettings.printSettings();

		CMTWorkload t = new CMTWorkload();
		t.loadSettings(args);
		t.setUp();

		switch (t.method) {
		case 1:
			t.runWorkload();
			break;
        case 2:
        	t.testWorkload();
			break;
		default:
			break;
		}
	}
}
