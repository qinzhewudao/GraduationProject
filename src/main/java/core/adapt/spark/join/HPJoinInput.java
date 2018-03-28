package core.adapt.spark.join;

/**
 * Created by ylu on 12/3/15.
 */


import java.util.*;


import org.apache.commons.io.FilenameUtils;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.Path;


import com.google.common.primitives.Ints;


import core.adapt.AccessMethod.PartitionSplit;
import core.adapt.JoinQuery;
import core.adapt.iterator.PostFilterIterator;
import core.adapt.iterator.RepartitionIterator;

public class HPJoinInput {

    protected Map<Integer, FileStatus> partitionIdFileMap;
    protected Map<Integer, Long> partitionIdSizeMap;

    public HPJoinInput(){
    }

    public void initialize(List<FileStatus> files) {
        partitionIdFileMap = new HashMap<Integer, FileStatus>();
        partitionIdSizeMap = new HashMap<Integer, Long>();
        for (FileStatus file : files) {
            try {
                String fileName = FilenameUtils.getName(file.getPath().toString());
                int id = Integer.parseInt(fileName);
                partitionIdFileMap.put(id, file);
                partitionIdSizeMap.put(id, file.getLen());
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }
    }

    public PartitionSplit[] getFullScan(JoinQuery q) {
        return new PartitionSplit[]{new PartitionSplit(
                Ints.toArray(partitionIdFileMap.keySet()),
                new PostFilterIterator(q.castToQuery()))};
    }

    public PartitionSplit[] getRepartitionScan(JoinQuery q) {
        return new PartitionSplit[]{new PartitionSplit(
                Ints.toArray(partitionIdFileMap.keySet()),
                new RepartitionIterator(q.castToQuery()))};
    }

    // utility methods

    public Path[] getPaths(int[] partitionIds) {

        Path[] splitFilesArr = new Path[partitionIds.length];
        for (int i = 0; i < splitFilesArr.length; i++)
            splitFilesArr[i] = partitionIdFileMap.get(partitionIds[i]).getPath();
        return splitFilesArr;
    }



    public long[] getLengths(int[] partitionIds) {
        long[] lengthsArr = new long[partitionIds.length];
        for (int i = 0; i < lengthsArr.length; i++) {
            lengthsArr[i] = partitionIdSizeMap.get(partitionIds[i]);
        }
        return lengthsArr;
    }
    public Map<Integer, Long> getPartitionIdSizeMap() {
        return partitionIdSizeMap;
    }
}
