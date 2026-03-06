package com.digital.video.gateway.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 雷达设备表管理类
 * radar_devices 表独立管理雷达，不依赖 devices 表（无外键约束）。
 */
public class RadarDeviceTable {
    private static final Logger logger = LoggerFactory.getLogger(RadarDeviceTable.class);

    /**
     * 创建雷达设备相关表
     */
    public static void createTables(Connection connection) throws SQLException {
        // radar_devices 表：无 devices 外键，雷达独立管理
        String createRadarDevicesTable = "CREATE TABLE IF NOT EXISTS radar_devices (" +
                "id SERIAL PRIMARY KEY, " +
                "device_id TEXT UNIQUE NOT NULL, " +
                "radar_ip TEXT NOT NULL, " +
                "radar_name TEXT, " +
                "assembly_id TEXT, " +
                "radar_serial TEXT, " +
                "status INTEGER DEFAULT 0, " +
                "current_background_id TEXT, " +
                "coordinate_transform TEXT, " +
                "created_at TIMESTAMP DEFAULT NOW(), " +
                "updated_at TIMESTAMP DEFAULT NOW(), " +
                "FOREIGN KEY (assembly_id) REFERENCES assemblies(assembly_id)" +
                ")";

        // 创建索引
        String createIndex =
                "CREATE INDEX IF NOT EXISTS idx_radar_devices_device_id ON radar_devices(device_id); " +
                "CREATE INDEX IF NOT EXISTS idx_radar_devices_assembly_id ON radar_devices(assembly_id); " +
                "CREATE INDEX IF NOT EXISTS idx_radar_devices_status ON radar_devices(status);";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createRadarDevicesTable);
            stmt.execute(createIndex);

            // 兼容旧版本：删除对 devices 表的外键约束（若存在），让雷达表独立管理
            dropDevicesFK(stmt);

            // 兼容旧版本：添加缺失列
            alterAddColumnIfMissing(stmt, "radar_serial", "ALTER TABLE radar_devices ADD COLUMN radar_serial TEXT");

            // radar_serial 唯一索引
            try {
                stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_radar_devices_serial ON radar_devices(radar_serial);");
            } catch (SQLException e) {
                logger.debug("创建 radar_serial 唯一索引失败或已存在: {}", e.getMessage());
            }

            logger.info("雷达设备表创建/迁移成功");
        }
    }

    /**
     * 删除 radar_devices → devices 的外键约束（若存在）
     */
    private static void dropDevicesFK(Statement stmt) {
        try {
            // 查询约束名
            String findFk = "SELECT conname FROM pg_constraint " +
                    "WHERE conrelid = 'radar_devices'::regclass " +
                    "AND confrelid = 'devices'::regclass " +
                    "AND contype = 'f'";
            var rs = stmt.executeQuery(findFk);
            while (rs.next()) {
                String constraintName = rs.getString("conname");
                stmt.execute("ALTER TABLE radar_devices DROP CONSTRAINT IF EXISTS \"" + constraintName + "\"");
                logger.info("已删除 radar_devices 对 devices 的外键约束: {}", constraintName);
            }
        } catch (Exception e) {
            logger.debug("删除 devices 外键时遇到异常（可能不存在，可忽略）: {}", e.getMessage());
        }
    }

    private static void alterAddColumnIfMissing(Statement stmt, String columnName, String ddl) {
        try {
            stmt.execute(ddl);
            logger.info("为 radar_devices 表添加列: {}", columnName);
        } catch (SQLException e) {
            logger.debug("列 {} 可能已存在: {}", columnName, e.getMessage());
        }
    }
}

