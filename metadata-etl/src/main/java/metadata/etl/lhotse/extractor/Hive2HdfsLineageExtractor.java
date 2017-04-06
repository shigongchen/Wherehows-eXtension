package metadata.etl.lhotse.extractor;

import metadata.etl.lhotse.LzTaskExecRecord;
import metadata.etl.utils.XmlParser;
import metadata.etl.utils.hiveparser.HiveSqlAnalyzer;
import metadata.etl.utils.hiveparser.HiveSqlType;
import wherehows.common.schemas.LineageRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by thomas young on 3/31/17.
 */
public class Hive2HdfsLineageExtractor implements BaseLineageExtractor {

    @Override
    public List<LineageRecord> getLineageRecord(String logLocation, LzTaskExecRecord lzTaskExecRecord,
                                                int defaultDatabaseId) {
        List<LineageRecord> lineageRecords = new ArrayList<>();
        XmlParser xmlParser = new XmlParser(logLocation);

        // get info from logs
        String destPath = xmlParser.getExtProperty("extProperties/entry/destFilePath");
        String sql = xmlParser.getExtProperty("extProperties/entry/filterSQL");

        // parse the hive table from sql
        List<String> isrcTableNames = new ArrayList<String>();
        List<String> idesTableNames = new ArrayList<String>();
        String opType = HiveSqlAnalyzer.analyzeSql(sql, isrcTableNames, idesTableNames);
        if (opType.equals(HiveSqlType.QUERY)) {}

        long taskId = Long.parseLong(lzTaskExecRecord.taskId);
        String taskName = lzTaskExecRecord.taskName;
        long flowExecId = Long.parseLong(xmlParser.getExtProperty("curRunDate"));

        String flowPath = "/hive2hdfs/" + taskName + "/" + flowExecId;
        String operation = null;
        long num = 0L;

        // source lineage record.
        for (String sourcePath: isrcTableNames) {
            LineageRecord lineageRecord = new LineageRecord(lzTaskExecRecord.appId, flowExecId, taskName, taskId);
            lineageRecord.setDatasetInfo(defaultDatabaseId, sourcePath, "hive");

            lineageRecord.setOperationInfo("source", operation, num, num,
                    num, num, lzTaskExecRecord.taskStartTime, lzTaskExecRecord.taskEndTime, flowPath);
            lineageRecords.add(lineageRecord);
        }

        // target lineage record.
        LineageRecord lineageRecord = new LineageRecord(lzTaskExecRecord.appId, flowExecId, taskName, taskId);
        lineageRecord.setDatasetInfo(defaultDatabaseId, destPath, "hdfs");
        lineageRecord.setOperationInfo("target", operation, num, num,
                num, num, lzTaskExecRecord.taskStartTime, lzTaskExecRecord.taskEndTime, flowPath);
        lineageRecords.add(lineageRecord);

        return lineageRecords;
    }
}