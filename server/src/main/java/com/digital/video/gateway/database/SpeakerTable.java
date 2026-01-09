package com.digital.video.gateway.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 音柱设备表管理类
 */
public class SpeakerTable {
    private static final Logger logger = LoggerFactory.getLogger(SpeakerTable.class);

    /**
     * 创建音柱设备相关表
     */
    public static void createTables(Connection connection) throws SQLException {
        // speakers 表
        String createSpeakersTable = "CREATE TABLE IF NOT EXISTS speakers (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "device_id TEXT UNIQUE NOT NULL, " +
            "name TEXT NOT NULL, " +
            "api_endpoint TEXT, " +
            "api_type TEXT DEFAULT 'http', " +
            "api_config TEXT, " +
            "status TEXT DEFAULT 'offline', " +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
            "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ")";

        // 创建索引
        String createIndex = "CREATE INDEX IF NOT EXISTS idx_speakers_device_id ON speakers(device_id); " +
            "CREATE INDEX IF NOT EXISTS idx_speakers_status ON speakers(status);";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createSpeakersTable);
            stmt.execute(createIndex);
            logger.info("音柱设备表创建成功");
        }
    }
}
