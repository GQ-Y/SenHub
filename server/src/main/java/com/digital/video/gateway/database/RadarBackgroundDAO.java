package com.digital.video.gateway.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 雷达背景模型DAO操作类
 */
public class RadarBackgroundDAO {
    private static final Logger logger = LoggerFactory.getLogger(RadarBackgroundDAO.class);
    private final Connection connection;

    public RadarBackgroundDAO(Connection connection) {
        this.connection = connection;
    }

    /**
     * 保存或更新背景模型
     */
    public boolean save(RadarBackground background) {
        if (background.getBackgroundId() == null) {
            background.setBackgroundId("bg_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8));
        }
        
        String sql = "INSERT OR REPLACE INTO radar_backgrounds " +
                "(background_id, device_id, assembly_id, frame_count, point_count, grid_resolution, " +
                "duration_seconds, file_path, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, background.getBackgroundId());
            pstmt.setString(2, background.getDeviceId());
            pstmt.setString(3, background.getAssemblyId());
            pstmt.setInt(4, background.getFrameCount());
            pstmt.setInt(5, background.getPointCount());
            pstmt.setFloat(6, background.getGridResolution());
            pstmt.setInt(7, background.getDurationSeconds());
            pstmt.setString(8, background.getFilePath());
            pstmt.setString(9, background.getStatus());
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.error("保存背景模型失败: {}", background.getBackgroundId(), e);
            return false;
        }
    }

    /**
     * 根据backgroundId获取背景模型
     */
    public RadarBackground getByBackgroundId(String backgroundId) {
        String sql = "SELECT * FROM radar_backgrounds WHERE background_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, backgroundId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return RadarBackground.fromResultSet(rs);
            }
        } catch (SQLException e) {
            logger.error("查询背景模型失败: {}", backgroundId, e);
        }
        return null;
    }

    /**
     * 获取设备的所有背景模型
     */
    public List<RadarBackground> getByDeviceId(String deviceId) {
        List<RadarBackground> backgrounds = new ArrayList<>();
        String sql = "SELECT * FROM radar_backgrounds WHERE device_id = ? ORDER BY created_at DESC";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, deviceId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                backgrounds.add(RadarBackground.fromResultSet(rs));
            }
        } catch (SQLException e) {
            logger.error("查询设备背景模型失败: {}", deviceId, e);
        }
        return backgrounds;
    }

    /**
     * 更新背景模型
     */
    public boolean update(RadarBackground background) {
        String sql = "UPDATE radar_backgrounds SET frame_count = ?, point_count = ?, status = ? WHERE background_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, background.getFrameCount());
            pstmt.setInt(2, background.getPointCount());
            pstmt.setString(3, background.getStatus());
            pstmt.setString(4, background.getBackgroundId());
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("更新背景模型失败: {}", background.getBackgroundId(), e);
            return false;
        }
    }

    /**
     * 获取背景点云数据
     */
    public List<RadarBackgroundPoint> getPointsByBackgroundId(String backgroundId) {
        List<RadarBackgroundPoint> points = new ArrayList<>();
        String sql = "SELECT * FROM radar_background_points WHERE background_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, backgroundId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                points.add(RadarBackgroundPoint.fromResultSet(rs));
            }
        } catch (SQLException e) {
            logger.error("查询背景点云失败: {}", backgroundId, e);
        }
        return points;
    }

    /**
     * 删除背景模型
     */
    public boolean delete(String backgroundId) {
        // 先删除背景点
        String deletePointsSql = "DELETE FROM radar_background_points WHERE background_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(deletePointsSql)) {
            pstmt.setString(1, backgroundId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("删除背景点云失败: {}", backgroundId, e);
        }
        
        // 再删除背景模型
        String sql = "DELETE FROM radar_backgrounds WHERE background_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, backgroundId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("删除背景模型失败: {}", backgroundId, e);
            return false;
        }
    }
}
