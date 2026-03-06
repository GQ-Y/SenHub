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
        String sql = "INSERT INTO radar_devices " +
                "(device_id, radar_ip, radar_name, assembly_id, radar_serial, status, current_background_id, " +
                "coordinate_transform, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, device.getDeviceId());
            pstmt.setString(2, device.getRadarIp());
            pstmt.setString(3, device.getRadarName());
            pstmt.setString(4, device.getAssemblyId());
            pstmt.setString(5, device.getRadarSerial());
            pstmt.setInt(6, device.getStatus());
            pstmt.setString(7, device.getCurrentBackgroundId());
            pstmt.setString(8, device.getCoordinateTransform());
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
     * 根据序列号获取雷达设备
     */
    public RadarDevice getBySerial(String radarSerial) {
        String sql = "SELECT * FROM radar_devices WHERE radar_serial = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, radarSerial);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return RadarDevice.fromResultSet(rs);
            }
        } catch (SQLException e) {
            logger.error("根据序列号查询雷达设备失败: {}", radarSerial, e);
        }
        return null;
    }

    /**
     * 更新雷达设备状态
     */
    public boolean updateStatus(String deviceId, int status) {
        String sql = "UPDATE radar_devices SET status = ?, updated_at = NOW() WHERE device_id = ?";
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
     * 根据序列号更新雷达设备状态
     */
    public boolean updateStatusBySerial(String radarSerial, int status) {
        String sql = "UPDATE radar_devices SET status = ?, updated_at = NOW() WHERE radar_serial = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, status);
            pstmt.setString(2, radarSerial);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("根据序列号更新雷达设备状态失败: {}", radarSerial, e);
            return false;
        }
    }
    
    /**
     * 根据序列号更新雷达设备的 IP 地址
     * 用于 SDK 自动发现设备后同步 IP 变化
     */
    public boolean updateIpBySerial(String radarSerial, String newIp) {
        String sql = "UPDATE radar_devices SET radar_ip = ?, updated_at = NOW() WHERE radar_serial = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, newIp);
            pstmt.setString(2, radarSerial);
            int rows = pstmt.executeUpdate();
            if (rows > 0) {
                logger.info("根据序列号更新雷达设备IP: serial={}, newIp={}", radarSerial, newIp);
            }
            return rows > 0;
        } catch (SQLException e) {
            logger.error("根据序列号更新雷达设备IP失败: serial={}, newIp={}", radarSerial, newIp, e);
            return false;
        }
    }
    
    /**
     * 更新设备的 IP 和状态（同时更新）
     */
    public boolean updateIpAndStatus(String deviceId, String newIp, int status) {
        String sql = "UPDATE radar_devices SET radar_ip = ?, status = ?, updated_at = NOW() WHERE device_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, newIp);
            pstmt.setInt(2, status);
            pstmt.setString(3, deviceId);
            int rows = pstmt.executeUpdate();
            if (rows > 0) {
                logger.info("更新雷达设备IP和状态: deviceId={}, newIp={}, status={}", deviceId, newIp, status);
            }
            return rows > 0;
        } catch (SQLException e) {
            logger.error("更新雷达设备IP和状态失败: deviceId={}", deviceId, e);
            return false;
        }
    }

    /**
     * 更新设备序列号（SN）
     */
    public boolean updateSerial(String deviceId, String serial) {
        String sql = "UPDATE radar_devices SET radar_serial = ?, updated_at = NOW() WHERE device_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, serial);
            pstmt.setString(2, deviceId);
            int rows = pstmt.executeUpdate();
            if (rows > 0) {
                logger.info("更新雷达设备SN: deviceId={}, serial={}", deviceId, serial);
            }
            return rows > 0;
        } catch (SQLException e) {
            logger.error("更新雷达设备SN失败: deviceId={}", deviceId, e);
            return false;
        }
    }

    /**
     * 更新当前背景模型ID
     */
    public boolean updateCurrentBackground(String deviceId, String backgroundId) {
        String sql = "UPDATE radar_devices SET current_background_id = ?, updated_at = NOW() WHERE device_id = ?";
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
