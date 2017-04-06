/**
 * Copyright 2017 tencent. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 */
package metadata.etl.lhotse;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import metadata.etl.lhotse.extractor.BaseLineageExtractor;
import metadata.etl.lhotse.extractor.Hive2HdfsLineageExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wherehows.common.Constant;
import wherehows.common.schemas.LineageRecord;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by thomas on 3/28/17.
 */
public class LzLineageExtractor {

    private static final Logger logger = LoggerFactory.getLogger(LzLineageExtractor.class);
    public static final String defaultLogLocation = "/usr/local/lhotse_runners/log/";

    /**
     * Reference: Get one job's lineage.
     * Process :
     * 1 get execution log from lhotse base server
     * 2 get hadoop job id from execution log
     * 3 get input, output from execution log and hadoop conf, normalize the path
     * 4 construct the Lineage Record
     *
     * @return one lhotse task's lineage
     */
    public static List<LineageRecord> extractLineage(LzExecMessage message)
            throws Exception {

        List<LineageRecord> jobLineage = new ArrayList<>();
        LzTaskExecRecord lzRecord = message.lzTaskExecRecord;
        String logLocation = null;

        if (message.prop.getProperty(Constant.LZ_LINEAGE_LOG_REMOTE, "false").equalsIgnoreCase("false")) {
            // the full path
            logLocation = message.prop.getProperty(Constant.LZ_LINEAGE_LOG_DEFAULT_DIR, defaultLogLocation);
            logLocation += String.format("logtasklog/%d/%s", lzRecord.taskType, lzRecord.taskId);
        } else {
            // move the log file from remote host to local host
            String remoteLogLocation = message.prop.getProperty(Constant.LZ_REMOTE_LOG_DIR, defaultLogLocation);
            remoteLogLocation += String.format("logtasklog/%d/%s", lzRecord.taskType, lzRecord.taskId);

            String localLogFile = message.prop.getProperty(Constant.LZ_LINEAGE_LOG_DEFAULT_DIR);
            localLogFile += String.format("logtasklog/%d/%s", lzRecord.taskType, lzRecord.taskId);
            new File(localLogFile).getParentFile().mkdirs();

            JSch jsch = new JSch();
            Session session = null;
            try {
                // set up session
                session = jsch.getSession(message.prop.getProperty(Constant.LZ_REMOTE_USER_KEY),
                                message.prop.getProperty(Constant.LZ_REMOTE_MACHINE_KEY));
                // use private key instead of username/password
                session.setConfig(
                        "PreferredAuthentications",
                        "publickey,gssapi-with-mic,keyboard-interactive,password");
                jsch.addIdentity(message.prop.getProperty(Constant.LZ_PRIVATE_KEY_LOCATION_KEY));
                java.util.Properties config = new java.util.Properties();
                config.put("StrictHostKeyChecking", "no");
                session.setConfig(config);
                session.connect();

                // copy remote log file to localhost.
                ChannelSftp channelSftp = (ChannelSftp) session.openChannel("sftp");
                channelSftp.connect();
                channelSftp.get(remoteLogLocation, localLogFile);
                channelSftp.exit();

                logLocation = localLogFile;
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                session.disconnect();
            }
        }

        // for debug.
        if (logLocation == null) {
            logger.error("log file location error!");
        } else {
            logger.info("log file location: %s", logLocation);
        }

        BaseLineageExtractor lineageExtractor = null;
        switch (lzRecord.taskType) {
            case 72:
                lineageExtractor = new Hive2HdfsLineageExtractor();
                break;
            default:
                throw new Exception("Not Supported Task Type!");
        }
        Integer defaultDatabaseId = Integer.valueOf(message.prop.getProperty(Constant.LZ_DEFAULT_HADOOP_DATABASE_ID_KEY));
        if (lineageExtractor != null) {
            List<LineageRecord> lineageRecords = lineageExtractor.getLineageRecord(logLocation, lzRecord, defaultDatabaseId);
            jobLineage.addAll(lineageRecords);
        }
        return jobLineage;
    }

    /**
     * Extract and write to database
     * @param message
     * @throws Exception
     */
    public static void extract(LzExecMessage message)
            throws Exception {
        try{
            List<LineageRecord> result = extractLineage(message);
            for (LineageRecord lr : result) {
                message.databaseWriter.append(lr);
            }
            logger.info(String.format("%03d lineage records extracted from [%s]", result.size(), message.toString()));
            message.databaseWriter.flush();
        } catch (Exception e) {
            logger.error(String.format("Failed to extract lineage info from [%s].\n%s", message.toString(), e.getMessage()));
        }
    }
}