package com.digital.video.gateway.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 报警记录表管理类
 */
public class AlarmRecordTable {
    private static final Logger logger = LoggerFactory.getLogger(AlarmRecordTable.class);

    /**
     * 创建报警记录相关表
     */
    public static void createTables(Connection connection) throws SQLException {
        // alarm_records 表
        String createAlarmRecordsTable = "CREATE TABLE IF NOT EXISTS alarm_records (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "alarm_id TEXT UNIQUE NOT NULL, " +
            "device_id TEXT NOT NULL, " +
            "assembly_id TEXT, " +
            "alarm_type TEXT NOT NULL, " +
            "alarm_level TEXT DEFAULT 'warning', " +
            "channel INTEGER, " +
            "alarm_data TEXT, " +
            "capture_url TEXT, " +
            "video_url TEXT, " +
            "status TEXT DEFAULT 'pending', " +
            "mqtt_sent INTEGER DEFAULT 0, " +
            "speaker_triggered INTEGER DEFAULT 0, " +
            "recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
            "processed_at TIMESTAMP" +
            ")";

        // 创建索引
        String createIndex = "CREATE INDEX IF NOT EXISTS idx_alarm_records_alarm_id ON alarm_records(alarm_id); " +
            "CREATE INDEX IF NOT EXISTS idx_alarm_records_device_id ON alarm_records(device_id); " +
            "CREATE INDEX IF NOT EXISTS idx_alarm_records_assembly_id ON alarm_records(assembly_id); " +
            "CREATE INDEX IF NOT EXISTS idx_alarm_records_alarm_type ON alarm_records(alarm_type); " +
            "CREATE INDEX IF NOT EXISTS idx_alarm_records_recorded_at ON alarm_records(recorded_at); " +
            "CREATE INDEX IF NOT EXISTS idx_alarm_records_status ON alarm_records(status);";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createAlarmRecordsTable);
            stmt.execute(createIndex);
            logger.info("报警记录表创建成功");
        }
    }
}
