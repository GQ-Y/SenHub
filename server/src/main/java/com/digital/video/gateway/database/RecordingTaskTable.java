package com.digital.video.gateway.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 录像任务表管理类
 */
public class RecordingTaskTable {
    private static final Logger logger = LoggerFactory.getLogger(RecordingTaskTable.class);

    /**
     * 创建录像任务相关表
     */
    public static void createTables(Connection connection) throws SQLException {
        // recording_tasks 表
        String createRecordingTasksTable = "CREATE TABLE IF NOT EXISTS recording_tasks (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "task_id TEXT UNIQUE NOT NULL, " +
            "device_id TEXT NOT NULL, " +
            "channel INTEGER NOT NULL, " +
            "start_time TEXT NOT NULL, " +
            "end_time TEXT NOT NULL, " +
            "local_file_path TEXT, " +
            "oss_url TEXT, " +
            "status TEXT DEFAULT 'pending', " +
            "progress INTEGER DEFAULT 0, " +
            "download_handle INTEGER, " +
            "error_message TEXT, " +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
            "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ")";

        // 创建索引
        String createIndex = "CREATE INDEX IF NOT EXISTS idx_recording_tasks_task_id ON recording_tasks(task_id); " +
            "CREATE INDEX IF NOT EXISTS idx_recording_tasks_device_id ON recording_tasks(device_id); " +
            "CREATE INDEX IF NOT EXISTS idx_recording_tasks_status ON recording_tasks(status); " +
            "CREATE INDEX IF NOT EXISTS idx_recording_tasks_created_at ON recording_tasks(created_at);";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createRecordingTasksTable);
            stmt.execute(createIndex);
            logger.info("录像任务表创建成功");
        }
    }
}
