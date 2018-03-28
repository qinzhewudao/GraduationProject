package core.adapt.spark.join;


import core.adapt.AccessMethod.PartitionSplit;
import core.adapt.JoinQuery;
import core.common.globals.Globals;
import core.common.globals.TableInfo;
import core.common.index.MDIndex;
import core.utils.HDFSUtils;
import core.utils.SparkUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaSparkContext;
import core.utils.ConfUtils;
import org.apache.spark.api.java.function.PairFunction;

import scala.Tuple2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by ylu on 1/6/16.
 */


public class SparkJoinQuery {

    public static class Mapper implements PairFunction<Tuple2<LongWritable, Tuple2<Text, Text>>, LongWritable, Text> {
        String Delimiter;
        String Splitter;
        int partitionKey;
        public Mapper(String Delimiter, int partitionKey) {
            this.Delimiter = Delimiter;
            this.partitionKey = partitionKey;
            if (Delimiter.equals("|"))
                Splitter = "\\|";
            else
                Splitter = Delimiter;
        }

        @Override
        public Tuple2<LongWritable, Text> call(Tuple2<LongWritable, Tuple2<Text, Text>> x) throws Exception {
            String s1 = x._2()._1().toString();
            String s2 = x._2()._2().toString();
            String value = s1 + Delimiter + s2;
            long key = Long.parseLong(value.split(Splitter)[partitionKey]);
            return new Tuple2<LongWritable, Text>(new LongWritable(key), new Text(value));
        }
    }

    private SparkJoinQueryConf queryConf;
    private JavaSparkContext ctx;
    private ConfUtils cfg;

    private String Delimiter = "|";
    private String joinStrategy = "Heuristic";

    public SparkJoinQuery(ConfUtils config) {
        this.cfg = config;
        SparkConf sconf = SparkUtils.getSparkConf(this.getClass().getName(), config);

        try {
            sconf.registerKryoClasses(new Class<?>[]{
                    Class.forName("org.apache.hadoop.io.LongWritable"),
                    Class.forName("org.apache.hadoop.io.Text")
            });
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        ctx = new JavaSparkContext(sconf);
        ctx.hadoopConfiguration().setBoolean(
                FileInputFormat.INPUT_DIR_RECURSIVE, true);
        ctx.hadoopConfiguration().set("fs.hdfs.impl",
                org.apache.hadoop.hdfs.DistributedFileSystem.class.getName());
        queryConf = new SparkJoinQueryConf(ctx.hadoopConfiguration());

        queryConf.setHDFSReplicationFactor(cfg.getHDFS_REPLICATION_FACTOR());
        queryConf.setHadoopHome(cfg.getHADOOP_HOME());
        queryConf.setZookeeperHosts(cfg.getZOOKEEPER_HOSTS());
        queryConf.setMaxSplitSize(4L * 1024 * 1024 * 1024); // 4 GB
        //queryConf.setMaxSplitSize(400L * 1024 * 1024); // 400 MB
        queryConf.setWorkerNum(8);
    }

    public JavaSparkContext getSparkContext(){
        return ctx;
    }

    public void setBufferSize(long size){
        queryConf.setMaxSplitSize(size);
    }

    public void setDelimiter(String delimiter) {
        Delimiter = delimiter;
    }

    public String getDelimiter() {
        return Delimiter;
    }

	/* SparkJoinQuery */

    public JavaPairRDD<LongWritable, Text> createJoinRDD(String hdfsPath) {
        queryConf.setReplicaId(0);

        return ctx.newAPIHadoopFile(cfg.getHADOOP_NAMENODE() + hdfsPath,
                SparkJoinInputFormat.class, LongWritable.class,
                Text.class, ctx.hadoopConfiguration());
    }


    public JavaPairRDD<LongWritable, Text> createJoinRDD(
            String dataset1, JoinQuery dataset1_query,
            String dataset2, JoinQuery dataset2_query,
            int partitionKey) {

        // set SparkQuery conf

        String hdfsPath = cfg.getHDFS_WORKING_DIR();

        queryConf.setWorkingDir(hdfsPath);
        queryConf.setJustAccess(false);

        Configuration conf = ctx.hadoopConfiguration();

        conf.set("DATASET1", dataset1);
        conf.set("DATASET2", dataset2);

        conf.set("DATASET1_QUERY", dataset1_query.toString());
        conf.set("DATASET2_QUERY", dataset2_query.toString());

        conf.set("PARTITION_KEY", Integer.toString(partitionKey)) ;

        conf.set("JOINALGO", joinStrategy);

        conf.set("DELIMITER", Delimiter);

        JoinPlanner planner = new JoinPlanner(conf);

        String hyperJoinInput = planner.hyperjoin;
        System.out.println("hyperJoinInput: " + hyperJoinInput);
        conf.set("DATASETINFO",hyperJoinInput);
        JavaPairRDD<LongWritable, Text> hyperjoin_rdd = createJoinRDD(hdfsPath);

        String input1 = planner.shufflejoin1;
        String input2 = planner.shufflejoin2;

        System.out.println("shuffleInput1: " + input1);
        System.out.println("shuffleInput2: " + input2);

        // set conf input;
        conf.set("DATASET_QUERY", dataset1_query.toString());
        conf.set("DATASETINFO", input1);
        conf.set("DATASET", dataset1);

        JavaPairRDD<LongWritable, Text> dataset1RDD = createSingleTableRDD(hdfsPath, dataset1_query);
        
        // set conf input;
        conf.set("DATASET_QUERY", dataset2_query.toString());
        conf.set("DATASETINFO", input2);
        conf.set("DATASET", dataset2);

        JavaPairRDD<LongWritable, Text> dataset2RDD = createSingleTableRDD(hdfsPath, dataset2_query);

        int numPartitions = 2000;

        JavaPairRDD<LongWritable, Tuple2<Text,Text> > join_rdd = dataset1RDD.join(dataset2RDD, numPartitions);

        JavaPairRDD<LongWritable, Text> shufflejoin_rdd = join_rdd.mapToPair(new Mapper(Delimiter, partitionKey));

        return hyperjoin_rdd.union(shufflejoin_rdd);
    }

    /* SingleTableScan  */

    public JavaPairRDD<LongWritable, Text> createSingleTableRDD(String hdfsPath,
                                                                JoinQuery q) {
        queryConf.setWorkingDir(hdfsPath);
        queryConf.setJoinQuery(q);

        return ctx.newAPIHadoopFile(cfg.getHADOOP_NAMENODE() + hdfsPath + "/" + q.getTable() + "/data",
                SparkScanInputFormat.class, LongWritable.class,
                Text.class, ctx.hadoopConfiguration());
    }

    /* for tpch 6 */

    public JavaPairRDD<LongWritable, Text> createScanRDD(String dataset, JoinQuery q) {

        // set SparkQuery conf

        String hdfsPath = cfg.getHDFS_WORKING_DIR();
        queryConf.setJustAccess(false);
        queryConf.setWorkingDir(hdfsPath);

        Configuration conf = ctx.hadoopConfiguration();


        conf.set("DATASET", dataset);
        conf.set("DATASET_QUERY", q.toString());
        conf.set("PARTITION_KEY", "0") ;
        conf.set("JOINALGO", joinStrategy);
        conf.set("DELIMITER", Delimiter);


        FileSystem fs = HDFSUtils.getFSByHadoopHome(queryConf.getHadoopHome());

        String workingDir = queryConf.getWorkingDir();
        //System.out.println("INFO working dir: " + workingDir);

        List<JoinQuery> dataset_queryWindow = JoinPlanner.loadQueries(dataset, queryConf);
        dataset_queryWindow.add(q);

        JoinPlanner.persistQueryToDisk(q, queryConf);

        Globals.loadTableInfo(dataset, queryConf.getWorkingDir(), fs);
        TableInfo dataset_tableInfo = Globals.getTableInfo(dataset);
        HPJoinInput dataset_hpinput = new HPJoinInput();
        Map<Integer, JoinAccessMethod> dataset_am = new HashMap<Integer, JoinAccessMethod>();
        Map<Integer, Integer> dataset_iterator_type = new HashMap<Integer, Integer>();
        Map<Integer, Integer> dataset_belong = new HashMap<Integer, Integer>();
        Map<Integer, ArrayList<Integer>> dataset_scan_blocks = new HashMap<Integer, ArrayList<Integer>>();
        Map<Integer, MDIndex.BucketInfo> bucketInfo = new HashMap<Integer, MDIndex.BucketInfo>();

        JoinPlanner.SmoothRepartition(dataset, q, dataset_queryWindow, dataset_tableInfo, dataset_hpinput, dataset_am, dataset_scan_blocks, dataset_iterator_type,dataset_belong, bucketInfo, queryConf, fs);
        ArrayList<PartitionSplit> shuffleJoinSplit = new  ArrayList<PartitionSplit>();
        JoinPlanner.extractShuffleJoin(q, shuffleJoinSplit, dataset_scan_blocks, dataset_iterator_type, dataset_hpinput.getPartitionIdSizeMap(), queryConf.getMaxSplitSize(), queryConf.getWorkerNum());

        String input = JoinPlanner.getShuffleJoinInputHelper(shuffleJoinSplit, dataset_hpinput);

        System.out.println("shuffleInput: " + input);

        conf.set("DATASETINFO", input);

        JavaPairRDD<LongWritable, Text> rdd = createSingleTableRDD(hdfsPath, q);
        return rdd;
    }

    public JavaPairRDD<LongWritable, Text> createCopartitionedRDD(String dataset1, JoinQuery dataset1_query,
                                                                  String dataset2, JoinQuery dataset2_query,
                                                                  int partitionKey){
        Configuration conf = ctx.hadoopConfiguration();

        conf.set("DATASET1", dataset1);
        conf.set("DATASET2", dataset2);

        conf.set("DATASET1_QUERY", dataset1_query.toString());
        conf.set("DATASET2_QUERY", dataset2_query.toString());

        conf.set("PARTITION_NUM", "200");

        conf.setInt("PARTITION_KEY",  partitionKey);

        conf.set("WORKING_DIR", cfg.getHDFS_WORKING_DIR());
        conf.set("HADOOP_HOME",cfg.getHADOOP_HOME());
        conf.set("ZOOKEEPER_HOSTS",cfg.getZOOKEEPER_HOSTS());
        conf.setInt("HDFS_REPLICATION_FACTOR",cfg.getHDFS_REPLICATION_FACTOR());
        conf.set("DELIMITER", "|");

        return ctx.newAPIHadoopFile(cfg.getHADOOP_NAMENODE() + cfg.getHDFS_WORKING_DIR(),
                SparkJoinCopartitionedInputFormat.class, LongWritable.class,
                Text.class, ctx.hadoopConfiguration());
    }
}
