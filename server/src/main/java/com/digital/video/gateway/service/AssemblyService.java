package com.digital.video.gateway.service;

import com.digital.video.gateway.database.Assembly;
import com.digital.video.gateway.database.AssemblyDevice;
import com.digital.video.gateway.database.Database;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 装置管理服务
 */
public class AssemblyService {
    private static final Logger logger = LoggerFactory.getLogger(AssemblyService.class);
    private final Database database;

    public AssemblyService(Database database) {
        this.database = database;
    }

    /**
     * 获取装置列表
     */
    public List<Assembly> getAssemblies(String search, String status) {
        List<Assembly> assemblies = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM assemblies WHERE 1=1");
        List<String> params = new ArrayList<>();

        if (search != null && !search.isEmpty()) {
            sql.append(" AND (name LIKE ? OR location LIKE ? OR description LIKE ?)");
            String searchPattern = "%" + search + "%";
            params.add(searchPattern);
            params.add(searchPattern);
            params.add(searchPattern);
        }

        if (status != null && !status.isEmpty() && !status.equals("ALL")) {
            try {
                int statusInt = Integer.parseInt(status);
                sql.append(" AND status = ?");
                params.add(String.valueOf(statusInt));
            } catch (NumberFormatException e) {
                logger.warn("无效的状态参数: {}", status);
            }
        }

        sql.append(" ORDER BY created_at DESC");

        Connection conn = database.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                pstmt.setString(i + 1, params.get(i));
            }
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                assemblies.add(Assembly.fromResultSet(rs));
            }
        } catch (SQLException e) {
            logger.error("获取装置列表失败", e);
        }
        return assemblies;
    }

    /**
     * 获取装置详情
     */
    public Assembly getAssembly(String assemblyId) {
        String sql = "SELECT * FROM assemblies WHERE assembly_id = ?";
        Connection conn = database.getConnection();
        try (
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, assemblyId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return Assembly.fromResultSet(rs);
            }
        } catch (SQLException e) {
            logger.error("获取装置详情失败: {}", assemblyId, e);
        }
        return null;
    }

    /**
     * 创建装置
     */
    public Assembly createAssembly(Assembly assembly) {
        if (assembly.getAssemblyId() == null || assembly.getAssemblyId().isEmpty()) {
            assembly.setAssemblyId(UUID.randomUUID().toString());
        }
        String sql = "INSERT INTO assemblies (assembly_id, name, description, location, status, ptz_linkage_enabled, longitude, latitude) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        Connection conn = database.getConnection();
        try (
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, assembly.getAssemblyId());
            pstmt.setString(2, assembly.getName());
            pstmt.setString(3, assembly.getDescription());
            pstmt.setString(4, assembly.getLocation());
            pstmt.setInt(5, assembly.getStatus());
            pstmt.setInt(6, assembly.isPtzLinkageEnabled() ? 1 : 0);
            if (assembly.getLongitude() != null) pstmt.setDouble(7, assembly.getLongitude()); else pstmt.setNull(7, Types.DOUBLE);
            if (assembly.getLatitude() != null) pstmt.setDouble(8, assembly.getLatitude()); else pstmt.setNull(8, Types.DOUBLE);
            pstmt.executeUpdate();
            return getAssembly(assembly.getAssemblyId());
        } catch (SQLException e) {
            logger.error("创建装置失败", e);
            return null;
        }
    }

    /**
     * 更新装置
     */
    public Assembly updateAssembly(String assemblyId, Assembly assembly) {
        String sql = "UPDATE assemblies SET name = ?, description = ?, location = ?, status = ?, ptz_linkage_enabled = ?, longitude = ?, latitude = ?, updated_at = CURRENT_TIMESTAMP WHERE assembly_id = ?";
        Connection conn = database.getConnection();
        try (
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, assembly.getName());
            pstmt.setString(2, assembly.getDescription());
            pstmt.setString(3, assembly.getLocation());
            pstmt.setInt(4, assembly.getStatus());
            pstmt.setInt(5, assembly.isPtzLinkageEnabled() ? 1 : 0);
            if (assembly.getLongitude() != null) pstmt.setDouble(6, assembly.getLongitude()); else pstmt.setNull(6, Types.DOUBLE);
            if (assembly.getLatitude() != null) pstmt.setDouble(7, assembly.getLatitude()); else pstmt.setNull(7, Types.DOUBLE);
            pstmt.setString(8, assemblyId);
            pstmt.executeUpdate();
            return getAssembly(assemblyId);
        } catch (SQLException e) {
            logger.error("更新装置失败: {}", assemblyId, e);
            return null;
        }
    }

    /**
     * 删除装置
     */
    public boolean deleteAssembly(String assemblyId) {
        Connection conn = null;
        try {
            conn = database.getConnection();
            conn.setAutoCommit(false);

            // 删除关联的设备记录
            String deleteDevicesSql = "DELETE FROM assembly_devices WHERE assembly_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(deleteDevicesSql)) {
                pstmt.setString(1, assemblyId);
                pstmt.executeUpdate();
            }

            // 删除装置
            String deleteAssemblySql = "DELETE FROM assemblies WHERE assembly_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(deleteAssemblySql)) {
                pstmt.setString(1, assemblyId);
                int rows = pstmt.executeUpdate();
                conn.commit();
                return rows > 0;
            }
        } catch (SQLException e) {
            logger.error("删除装置失败: {}", assemblyId, e);
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    logger.error("回滚事务失败", ex);
                }
            }
            return false;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException e) {
                    logger.error("回滚 AutoCommit 失败", e);
                }
            }
        }
    }

    /**
     * 添加设备到装置
     */
    public AssemblyDevice addDeviceToAssembly(String assemblyId, String deviceId, String role, String positionInfo) {
        String sql = "INSERT OR REPLACE INTO assembly_devices (assembly_id, device_id, device_role, position_info, updated_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)";
        Connection conn = database.getConnection();
        try (
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, assemblyId);
            pstmt.setString(2, deviceId);
            pstmt.setString(3, role);
            pstmt.setString(4, positionInfo);
            pstmt.executeUpdate();
            return getAssemblyDevice(assemblyId, deviceId);
        } catch (SQLException e) {
            logger.error("添加设备到装置失败: assemblyId={}, deviceId={}", assemblyId, deviceId, e);
            return null;
        }
    }

    /**
     * 从装置移除设备
     */
    public boolean removeDeviceFromAssembly(String assemblyId, String deviceId) {
        String sql = "DELETE FROM assembly_devices WHERE assembly_id = ? AND device_id = ?";
        Connection conn = database.getConnection();
        try (
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, assemblyId);
            pstmt.setString(2, deviceId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("从装置移除设备失败: assemblyId={}, deviceId={}", assemblyId, deviceId, e);
            return false;
        }
    }

    /**
     * 获取装置的所有设备
     */
    public List<AssemblyDevice> getAssemblyDevices(String assemblyId) {
        List<AssemblyDevice> devices = new ArrayList<>();
        String sql = "SELECT * FROM assembly_devices WHERE assembly_id = ? ORDER BY priority DESC, created_at ASC";
        Connection conn = database.getConnection();
        try (
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, assemblyId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                devices.add(AssemblyDevice.fromResultSet(rs));
            }
        } catch (SQLException e) {
            logger.error("获取装置设备列表失败: {}", assemblyId, e);
        }
        return devices;
    }

    /**
     * 获取设备所属的所有装置
     */
    public List<Assembly> getAssembliesByDevice(String deviceId) {
        List<Assembly> assemblies = new ArrayList<>();
        String sql = "SELECT a.* FROM assemblies a INNER JOIN assembly_devices ad ON a.assembly_id = ad.assembly_id WHERE ad.device_id = ?";
        Connection conn = database.getConnection();
        try (
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, deviceId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                assemblies.add(Assembly.fromResultSet(rs));
            }
        } catch (SQLException e) {
            logger.error("获取设备所属装置列表失败: {}", deviceId, e);
        }
        return assemblies;
    }

    /**
     * 获取装置设备关联
     */
    /**
     * 获取装置中的设备关联信息
     */
    public AssemblyDevice getAssemblyDevice(String assemblyId, String deviceId) {
        String sql = "SELECT * FROM assembly_devices WHERE assembly_id = ? AND device_id = ?";
        Connection conn = database.getConnection();
        try (
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, assemblyId);
            pstmt.setString(2, deviceId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return AssemblyDevice.fromResultSet(rs);
            }
        } catch (SQLException e) {
            logger.error("获取装置设备关联失败: assemblyId={}, deviceId={}", assemblyId, deviceId, e);
        }
        return null;
    }

    /**
     * 更新装置中的设备角色
     */
    public boolean updateDeviceRole(String assemblyId, String deviceId, String role) {
        String sql = "UPDATE assembly_devices SET device_role = ? WHERE assembly_id = ? AND device_id = ?";
        Connection conn = database.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, role);
            pstmt.setString(2, assemblyId);
            pstmt.setString(3, deviceId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("更新装置设备角色失败", e);
            return false;
        }
    }
}
