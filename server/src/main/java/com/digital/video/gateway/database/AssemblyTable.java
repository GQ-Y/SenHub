package com.digital.video.gateway.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 装置表管理类
 */
public class AssemblyTable {
    private static final Logger logger = LoggerFactory.getLogger(AssemblyTable.class);

    /**
     * 创建装置相关表
     */
    public static void createTables(Connection connection) throws SQLException {
        // assemblies 表
        String createAssembliesTable = "CREATE TABLE IF NOT EXISTS assemblies (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "assembly_id TEXT UNIQUE NOT NULL, " +
                "name TEXT NOT NULL, " +
                "description TEXT, " +
                "location TEXT, " +
                "status INTEGER DEFAULT 1, " + // 0: 禁用, 1: 启用
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")";

        // assembly_devices 表
        String createAssemblyDevicesTable = "CREATE TABLE IF NOT EXISTS assembly_devices (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "assembly_id TEXT NOT NULL, " +
                "device_id TEXT NOT NULL, " +
                "device_role TEXT NOT NULL, " +
                "position_info TEXT, " +
                "priority INTEGER DEFAULT 0, " +
                "enabled INTEGER DEFAULT 1, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE(assembly_id, device_id)" +
                ")";

        // 创建索引
        String createIndex = "CREATE INDEX IF NOT EXISTS idx_assemblies_assembly_id ON assemblies(assembly_id); " +
                "CREATE INDEX IF NOT EXISTS idx_assemblies_status ON assemblies(status); " +
                "CREATE INDEX IF NOT EXISTS idx_assembly_devices_assembly_id ON assembly_devices(assembly_id); " +
                "CREATE INDEX IF NOT EXISTS idx_assembly_devices_device_id ON assembly_devices(device_id); " +
                "CREATE INDEX IF NOT EXISTS idx_assembly_devices_role ON assembly_devices(device_role);";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createAssembliesTable);
            try {
                stmt.execute("ALTER TABLE assemblies ADD COLUMN ptz_linkage_enabled INTEGER DEFAULT 0");
                logger.info("为 assemblies 表添加 ptz_linkage_enabled 列");
            } catch (SQLException e) {
                logger.debug("ptz_linkage_enabled 列可能已存在: {}", e.getMessage());
            }
            try {
                stmt.execute("ALTER TABLE assemblies ADD COLUMN longitude REAL");
                logger.info("为 assemblies 表添加 longitude 列");
            } catch (SQLException e) {
                if (e.getMessage() == null || !e.getMessage().toLowerCase().contains("duplicate column")) {
                    logger.debug("longitude 列可能已存在: {}", e.getMessage());
                }
            }
            try {
                stmt.execute("ALTER TABLE assemblies ADD COLUMN latitude REAL");
                logger.info("为 assemblies 表添加 latitude 列");
            } catch (SQLException e) {
                if (e.getMessage() == null || !e.getMessage().toLowerCase().contains("duplicate column")) {
                    logger.debug("latitude 列可能已存在: {}", e.getMessage());
                }
            }
            stmt.execute(createAssemblyDevicesTable);
            stmt.execute(createIndex);
            logger.info("装置相关表创建成功");
        }
    }
}
