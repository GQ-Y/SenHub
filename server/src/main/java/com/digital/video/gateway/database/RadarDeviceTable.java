package com.digital.video.gateway.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 雷达设备表管理类
 */
public class RadarDeviceTable {
    private static final Logger logger = LoggerFactory.getLogger(RadarDeviceTable.class);

    /**
     * 创建雷达设备相关表
     */
    public static void createTables(Connection connection) throws SQLException {
        // radar_devices 表
        String createRadarDevicesTable = "CREATE TABLE IF NOT EXISTS radar_devices (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "device_id TEXT UNIQUE NOT NULL, " +
                "radar_ip TEXT NOT NULL, " +
                "radar_name TEXT, " +
                "assembly_id TEXT, " +
                "status INTEGER DEFAULT 0, " + // 0:离线, 1:在线, 2:采集背景中
                "current_background_id TEXT, " +
                "coordinate_transform TEXT, " + // JSON格式的坐标系转换参数
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (device_id) REFERENCES devices(device_id), " +
                "FOREIGN KEY (assembly_id) REFERENCES assemblies(assembly_id)" +
                ")";

        // 创建索引
        String createIndex = "CREATE INDEX IF NOT EXISTS idx_radar_devices_device_id ON radar_devices(device_id); " +
                "CREATE INDEX IF NOT EXISTS idx_radar_devices_assembly_id ON radar_devices(assembly_id); " +
                "CREATE INDEX IF NOT EXISTS idx_radar_devices_status ON radar_devices(status);";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createRadarDevicesTable);
            stmt.execute(createIndex);
            logger.debug("雷达设备表创建成功");
        }
    }
}
