package com.digital.video.gateway.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 雷达背景模型表管理类
 */
public class RadarBackgroundTable {
    private static final Logger logger = LoggerFactory.getLogger(RadarBackgroundTable.class);

    /**
     * 创建雷达背景模型相关表
     */
    public static void createTables(Connection connection) throws SQLException {
        // radar_backgrounds 表
        String createRadarBackgroundsTable = "CREATE TABLE IF NOT EXISTS radar_backgrounds (" +
                "id SERIAL PRIMARY KEY, " +
                "background_id TEXT UNIQUE NOT NULL, " +
                "device_id TEXT NOT NULL, " +
                "assembly_id TEXT, " +
                "frame_count INTEGER NOT NULL, " +
                "point_count INTEGER NOT NULL, " +
                "grid_resolution FLOAT8 DEFAULT 0.05, " + // 体素分辨率（米），默认5cm
                "duration_seconds INTEGER NOT NULL, " + // 采集时长（秒）
                "file_path TEXT, " + // .pcd文件路径（可选）
                "status TEXT DEFAULT 'collecting', " + // collecting, ready, expired
                "created_at TIMESTAMP DEFAULT NOW(), " +
                "FOREIGN KEY (device_id) REFERENCES devices(device_id)" +
                ")";

        // radar_background_points 表
        String createRadarBackgroundPointsTable = "CREATE TABLE IF NOT EXISTS radar_background_points (" +
                "id SERIAL PRIMARY KEY, " +
                "background_id TEXT NOT NULL, " +
                "grid_key TEXT NOT NULL, " + // 网格索引键，格式: "x_y_z"（整数坐标）
                "center_x FLOAT8 NOT NULL, " + // 网格中心x坐标（米）
                "center_y FLOAT8 NOT NULL, " +
                "center_z FLOAT8 NOT NULL, " +
                "point_count INTEGER NOT NULL, " + // 该网格内的原始点数
                "mean_distance FLOAT8, " + // 平均距离
                "std_deviation FLOAT8, " + // 标准差（用于噪声过滤）
                "created_at TIMESTAMP DEFAULT NOW(), " +
                "FOREIGN KEY (background_id) REFERENCES radar_backgrounds(background_id), " +
                "UNIQUE(background_id, grid_key)" +
                ")";

        // 创建索引
        String createIndex = "CREATE INDEX IF NOT EXISTS idx_background_grid ON radar_background_points(background_id, grid_key); " +
                "CREATE INDEX IF NOT EXISTS idx_radar_backgrounds_device_id ON radar_backgrounds(device_id); " +
                "CREATE INDEX IF NOT EXISTS idx_radar_backgrounds_status ON radar_backgrounds(status);";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createRadarBackgroundsTable);
            stmt.execute(createRadarBackgroundPointsTable);
            stmt.execute(createIndex);
            logger.debug("雷达背景模型表创建成功");
        }
    }
}
