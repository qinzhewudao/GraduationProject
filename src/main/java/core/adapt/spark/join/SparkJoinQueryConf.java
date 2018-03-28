package core.adapt.spark.join;

import core.adapt.JoinQuery;

import org.apache.hadoop.conf.Configuration;


public class SparkJoinQueryConf {

    public final static String WORKING_DIR = "WORKING_DIR";
    public final static String FULL_SCAN = "FULL_SCAN";
    public final static String REPARTITION_SCAN = "REPARTITION_SCAN";
    public final static String JUST_ACCESS = "JUST_ACCESS";
    public final static String QUERY = "QUERY";
    public final static String MAX_SPLIT_SIZE = "MAX_SPLIT_SIZE";
    public final static String MIN_SPLIT_SIZE = "MIN_SPLIT_SIZE";
    public final static String ZOOKEEPER_HOSTS = "ZOOKEEPER_HOSTS";
    public final static String HADOOP_HOME = "HADOOP_HOME";
    public final static String REPLICA_ID = "REPLICA_ID";
    public final static String HDFS_REPLICATION_FACTOR = "HDFS_REPLICATION_FACTOR";
    public final static String SCHEMA = "SCHEMA";
    public final static String CARTILAGE_PROPERTIES = "CARTILAGE_PROPERTIES";
    public final static String WORKER_NUM = "WORKER_NUM";

    private Configuration conf;

    public SparkJoinQueryConf(Configuration conf) {
        this.conf = conf;
    }

    public void setWorkingDir(String dataset) {
        conf.set(WORKING_DIR, dataset);
    }

    public String getWorkingDir() {
        return conf.get(WORKING_DIR);
    }

    public void setFullScan(boolean flag) {
        conf.setBoolean(FULL_SCAN, flag);
    }

    public boolean getFullScan() {
        return conf.getBoolean(FULL_SCAN, false); // don't full scan by default
    }

    public void setJustAccess(boolean flag) {
        conf.setBoolean(JUST_ACCESS, flag);
    }

    public boolean getJustAccess() {
        return conf.getBoolean(JUST_ACCESS, true); // don't adapt by default,
        // i.e. just access
    }

    public void setJoinQuery(JoinQuery query) {
        conf.set(QUERY, query.toString());
    }

    public JoinQuery getQuery() {
        if (conf.get(QUERY) == null || conf.get(QUERY).equals("")) {
            throw new RuntimeException("No query set in query conf.");
        }

        return new JoinQuery(conf.get(QUERY));
    }

    public void setMaxSplitSize(long maxSplitSize) {
        conf.set(MAX_SPLIT_SIZE, "" + maxSplitSize);
    }

    public long getMaxSplitSize() {
        return Long.parseLong(conf.get(MAX_SPLIT_SIZE));
    }



    public void setMinSplitSize(long minSplitSize) {
        conf.set(MIN_SPLIT_SIZE, "" + minSplitSize);
    }

    public void setWorkerNum(int num){
        conf.set(WORKER_NUM, "" + num);
    }

    public int getWorkerNum(){
        return Integer.parseInt(conf.get(WORKER_NUM));
    }

    public long getMinSplitSize() {
        return Long.parseLong(conf.get(MIN_SPLIT_SIZE));
    }

    public void setZookeeperHosts(String hosts) {
        conf.set(ZOOKEEPER_HOSTS, "" + hosts);
    }

    public String getZookeeperHosts() {
        return conf.get(ZOOKEEPER_HOSTS);
    }

    public void setHadoopHome(String home) {
        conf.set(HADOOP_HOME, home);
    }

    public String getHadoopHome() {
        return conf.get(HADOOP_HOME);
    }

    public void setHDFSReplicationFactor(short f) {
        conf.set(HDFS_REPLICATION_FACTOR, Short.toString(f));
    }

    public short getHDFSReplicationFactor() {
        return Short.parseShort(conf.get(HDFS_REPLICATION_FACTOR));
    }

    public void setReplicaId(int numReplicas) {
        conf.set(REPLICA_ID, String.valueOf(numReplicas));
    }

    public int getReplicaId() {
        return Integer.parseInt(conf.get(REPLICA_ID));
    }

    public Configuration getConf() {
        return conf;
    }
}
