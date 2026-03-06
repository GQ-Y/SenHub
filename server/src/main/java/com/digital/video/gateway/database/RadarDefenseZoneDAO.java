package com.digital.video.gateway.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 雷达防区配置DAO操作类
 */
public class RadarDefenseZoneDAO {
    private static final Logger logger = LoggerFactory.getLogger(RadarDefenseZoneDAO.class);
    private final Connection connection;

    public RadarDefenseZoneDAO(Connection connection) {
        this.connection = connection;
    }

    /**
     * 保存或更新防区
     */
    public boolean save(RadarDefenseZone zone) {
        String sql = "INSERT INTO radar_defense_zones " +
                "(zone_id, device_id, assembly_id, background_id, zone_type, shrink_distance_cm, " +
                "min_x, max_x, min_y, max_y, min_z, max_z, camera_device_id, camera_channel, " +
                "coordinate_transform, enabled, name, description, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, zone.getZoneId());
            pstmt.setString(2, zone.getDeviceId());
            pstmt.setString(3, zone.getAssemblyId());
            pstmt.setString(4, zone.getBackgroundId());
            pstmt.setString(5, zone.getZoneType());
            pstmt.setObject(6, zone.getShrinkDistanceCm());
            pstmt.setObject(7, zone.getMinX());
            pstmt.setObject(8, zone.getMaxX());
            pstmt.setObject(9, zone.getMinY());
            pstmt.setObject(10, zone.getMaxY());
            pstmt.setObject(11, zone.getMinZ());
            pstmt.setObject(12, zone.getMaxZ());
            pstmt.setString(13, zone.getCameraDeviceId());
            pstmt.setObject(14, zone.getCameraChannel());
            pstmt.setString(15, zone.getCoordinateTransform());
            pstmt.setInt(16, zone.isEnabled() ? 1 : 0);
            pstmt.setString(17, zone.getName());
            pstmt.setString(18, zone.getDescription());
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.error("保存防区失败: {}", zone.getZoneId(), e);
            return false;
        }
    }

    /**
     * 根据zoneId获取防区
     */
    public RadarDefenseZone getByZoneId(String zoneId) {
        String sql = "SELECT * FROM radar_defense_zones WHERE zone_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, zoneId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return RadarDefenseZone.fromResultSet(rs);
            }
        } catch (SQLException e) {
            logger.error("查询防区失败: {}", zoneId, e);
        }
        return null;
    }

    /**
     * 获取设备的所有防区
     */
    public List<RadarDefenseZone> getByDeviceId(String deviceId) {
        List<RadarDefenseZone> zones = new ArrayList<>();
        String sql = "SELECT * FROM radar_defense_zones WHERE device_id = ? ORDER BY created_at DESC";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, deviceId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                zones.add(RadarDefenseZone.fromResultSet(rs));
            }
        } catch (SQLException e) {
            logger.error("查询设备防区失败: {}", deviceId, e);
        }
        return zones;
    }

    /**
     * 更新防区
     */
    public boolean update(RadarDefenseZone zone) {
        String sql = "UPDATE radar_defense_zones SET " +
                "background_id = ?, zone_type = ?, shrink_distance_cm = ?, " +
                "min_x = ?, max_x = ?, min_y = ?, max_y = ?, min_z = ?, max_z = ?, " +
                "camera_device_id = ?, camera_channel = ?, coordinate_transform = ?, " +
                "enabled = ?, name = ?, description = ?, updated_at = NOW() " +
                "WHERE zone_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, zone.getBackgroundId());
            pstmt.setString(2, zone.getZoneType());
            pstmt.setObject(3, zone.getShrinkDistanceCm());
            pstmt.setObject(4, zone.getMinX());
            pstmt.setObject(5, zone.getMaxX());
            pstmt.setObject(6, zone.getMinY());
            pstmt.setObject(7, zone.getMaxY());
            pstmt.setObject(8, zone.getMinZ());
            pstmt.setObject(9, zone.getMaxZ());
            pstmt.setString(10, zone.getCameraDeviceId());
            pstmt.setObject(11, zone.getCameraChannel());
            pstmt.setString(12, zone.getCoordinateTransform());
            pstmt.setInt(13, zone.isEnabled() ? 1 : 0);
            pstmt.setString(14, zone.getName());
            pstmt.setString(15, zone.getDescription());
            pstmt.setString(16, zone.getZoneId());
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("更新防区失败: {}", zone.getZoneId(), e);
            return false;
        }
    }

    /**
     * 删除防区
     */
    public boolean delete(String zoneId) {
        String sql = "DELETE FROM radar_defense_zones WHERE zone_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, zoneId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("删除防区失败: {}", zoneId, e);
            return false;
        }
    }

    /**
     * 启用/禁用防区
     */
    public boolean toggle(String zoneId, boolean enabled) {
        String sql = "UPDATE radar_defense_zones SET enabled = ?, updated_at = NOW() WHERE zone_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, enabled ? 1 : 0);
            pstmt.setString(2, zoneId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("切换防区状态失败: {}", zoneId, e);
            return false;
        }
    }
}
