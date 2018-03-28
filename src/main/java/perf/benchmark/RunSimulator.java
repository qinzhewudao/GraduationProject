package perf.benchmark;

import core.adapt.Query;
import core.simulator.Simulator;
import core.utils.ConfUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility used to run the simulator.
 * Loads queries and generates a log.
 */
public class RunSimulator {
    String tableName;

    String simName;

    String queriesFile;

    ConfUtils cfg;

    Query[] queries;

    // 0 => SinglePred
    // 1 => MultiPred
    int mode = -1;

	public void loadSettings(String[] args) {
		int counter = 0;
		while (counter < args.length) {
			switch (args[counter]) {
			case "--tableName":
				tableName = args[counter + 1];
				counter += 2;
				break;
			case "--simName":
				simName = args[counter+1];
				counter += 2;
				break;
            case "--queriesFile":
				queriesFile = args[counter + 1];
				counter += 2;
				break;
            case "--mode":
                mode = Integer.parseInt(args[counter + 1]);
                counter += 2;
                break;
			default:
				// Something we don't use
				counter += 2;
				break;
			}
		}
	}


	public void setup() {
        cfg = new ConfUtils(BenchmarkSettings.conf);
        List<Query> list = new ArrayList<>();

        File file = new File(queriesFile);
        BufferedReader reader = null;

        try {
            reader = new BufferedReader(new FileReader(file));
            String text;

            while ((text = reader.readLine()) != null) {
                list.add(new Query(text));
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
            }
        }

        System.out.println("Read " + list.size() + " queries from the file");

        queries = new Query[list.size()];
        queries = list.toArray(queries);
	}

    public void run() {
        Simulator sim  = new Simulator();
        sim.setUp(cfg, simName, tableName, queries);

        if (mode == 0) {
            sim.runOld();
        } else if (mode == 1) {
            sim.run();
        } else {
           System.out.println("Unknown mode");
        }
    }

    public static void main(String[] args) {
        BenchmarkSettings.loadSettings(args);
		BenchmarkSettings.printSettings();

		RunSimulator t = new RunSimulator();
		t.loadSettings(args);
		t.setup();
        t.run();
    }
}
