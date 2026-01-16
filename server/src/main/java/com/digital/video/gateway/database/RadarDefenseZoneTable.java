package com.digital.video.gateway.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 雷达防区配置表管理类
 */
public class RadarDefenseZoneTable {
    private static final Logger logger = LoggerFactory.getLogger(RadarDefenseZoneTable.class);

    /**
     * 创建雷达防区配置相关表
     */
    public static void createTables(Connection connection) throws SQLException {
        // radar_defense_zones 表
        String createRadarDefenseZonesTable = "CREATE TABLE IF NOT EXISTS radar_defense_zones (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "zone_id TEXT UNIQUE NOT NULL, " +
                "device_id TEXT NOT NULL, " +
                "assembly_id TEXT, " +
                "background_id TEXT NOT NULL, " +
                "zone_type TEXT NOT NULL, " + // 'shrink'（缩小距离）或'bounding_box'（x/y/z轴范围）
                "shrink_distance_cm INTEGER, " + // 缩小距离（厘米），zone_type='shrink'时使用
                "min_x REAL, " + // 防区边界（米），zone_type='bounding_box'时使用
                "max_x REAL, " +
                "min_y REAL, " +
                "max_y REAL, " +
                "min_z REAL, " +
                "max_z REAL, " +
                "camera_device_id TEXT, " + // 关联的摄像头设备ID
                "camera_channel INTEGER DEFAULT 1, " +
                "coordinate_transform TEXT, " + // 坐标系转换参数（JSON）
                "enabled INTEGER DEFAULT 1, " + // SQLite使用INTEGER表示BOOLEAN
                "name TEXT, " +
                "description TEXT, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (device_id) REFERENCES devices(device_id), " +
                "FOREIGN KEY (background_id) REFERENCES radar_backgrounds(background_id), " +
                "FOREIGN KEY (camera_device_id) REFERENCES devices(device_id)" +
                ")";

        // radar_intrusion_records 表
        String createRadarIntrusionRecordsTable = "CREATE TABLE IF NOT EXISTS radar_intrusion_records (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "record_id TEXT UNIQUE NOT NULL, " +
                "device_id TEXT NOT NULL, " +
                "assembly_id TEXT, " +
                "zone_id TEXT, " +
                "cluster_id TEXT, " + // 聚类ID
                "centroid_x REAL NOT NULL, " +
                "centroid_y REAL NOT NULL, " +
                "centroid_z REAL NOT NULL, " +
                "volume REAL, " + // 体积（立方米）
                "bbox_min_x REAL, " +
                "bbox_min_y REAL, " +
                "bbox_min_z REAL, " +
                "bbox_max_x REAL, " +
                "bbox_max_y REAL, " +
                "bbox_max_z REAL, " +
                "point_count INTEGER, " +
                "duration INTEGER, " + // 侵入时长（毫秒）
                "detected_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (device_id) REFERENCES devices(device_id), " +
                "FOREIGN KEY (zone_id) REFERENCES radar_defense_zones(zone_id)" +
                ")";

        // 创建索引
        String createIndex = "CREATE INDEX IF NOT EXISTS idx_radar_defense_zones_device_id ON radar_defense_zones(device_id); "
                +
                "CREATE INDEX IF NOT EXISTS idx_radar_defense_zones_background_id ON radar_defense_zones(background_id); "
                +
                "CREATE INDEX IF NOT EXISTS idx_radar_defense_zones_enabled ON radar_defense_zones(enabled); " +
                "CREATE INDEX IF NOT EXISTS idx_intrusion_device_time ON radar_intrusion_records(device_id, detected_at); "
                +
                "CREATE INDEX IF NOT EXISTS idx_intrusion_zone_id ON radar_intrusion_records(zone_id);";

        // 迁移：为现有表添加 duration 字段（如果不存在）
        String addDurationColumn = "ALTER TABLE radar_intrusion_records ADD COLUMN duration INTEGER;";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createRadarDefenseZonesTable);
            stmt.execute(createRadarIntrusionRecordsTable);
            stmt.execute(createIndex);
            // 尝试添加新列，忽略已存在错误
            try {
                stmt.execute(addDurationColumn);
            } catch (SQLException ignored) {
            }
            logger.debug("雷达防区配置表创建成功");
        }
    }
}
