package com.digital.video.gateway.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 雷达设备DAO操作类
 */
public class RadarDeviceDAO {
    private static final Logger logger = LoggerFactory.getLogger(RadarDeviceDAO.class);
    private final Connection connection;

    public RadarDeviceDAO(Connection connection) {
        this.connection = connection;
    }

    /**
     * 保存或更新雷达设备
     */
    public boolean saveOrUpdate(RadarDevice device) {
        String sql = "INSERT OR REPLACE INTO radar_devices " +
                "(device_id, radar_ip, radar_name, assembly_id, status, current_background_id, " +
                "coordinate_transform, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, device.getDeviceId());
            pstmt.setString(2, device.getRadarIp());
            pstmt.setString(3, device.getRadarName());
            pstmt.setString(4, device.getAssemblyId());
            pstmt.setInt(5, device.getStatus());
            pstmt.setString(6, device.getCurrentBackgroundId());
            pstmt.setString(7, device.getCoordinateTransform());
            pstmt.executeUpdate();
            logger.debug("雷达设备已保存: {}", device.getDeviceId());
            return true;
        } catch (SQLException e) {
            logger.error("保存雷达设备失败: {}", device.getDeviceId(), e);
            return false;
        }
    }

    /**
     * 根据deviceId获取雷达设备
     */
    public RadarDevice getByDeviceId(String deviceId) {
        String sql = "SELECT * FROM radar_devices WHERE device_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, deviceId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return RadarDevice.fromResultSet(rs);
            }
        } catch (SQLException e) {
            logger.error("查询雷达设备失败: {}", deviceId, e);
        }
        return null;
    }

    /**
     * 获取所有雷达设备
     */
    public List<RadarDevice> getAll() {
        List<RadarDevice> devices = new ArrayList<>();
        String sql = "SELECT * FROM radar_devices ORDER BY created_at DESC";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                devices.add(RadarDevice.fromResultSet(rs));
            }
        } catch (SQLException e) {
            logger.error("查询所有雷达设备失败", e);
        }
        return devices;
    }

    /**
     * 更新雷达设备状态
     */
    public boolean updateStatus(String deviceId, int status) {
        String sql = "UPDATE radar_devices SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE device_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, status);
            pstmt.setString(2, deviceId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("更新雷达设备状态失败: {}", deviceId, e);
            return false;
        }
    }

    /**
     * 更新当前背景模型ID
     */
    public boolean updateCurrentBackground(String deviceId, String backgroundId) {
        String sql = "UPDATE radar_devices SET current_background_id = ?, updated_at = CURRENT_TIMESTAMP WHERE device_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, backgroundId);
            pstmt.setString(2, deviceId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("更新雷达设备背景模型失败: {}", deviceId, e);
            return false;
        }
    }

    /**
     * 删除雷达设备
     */
    public boolean delete(String deviceId) {
        String sql = "DELETE FROM radar_devices WHERE device_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, deviceId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("删除雷达设备失败: {}", deviceId, e);
            return false;
        }
    }
}
