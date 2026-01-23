package com.digital.video.gateway.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 设备PTZ扩展表管理类
 * 用于存储球机的PTZ位置和GIS信息
 */
public class DevicePtzExtensionTable {
    private static final Logger logger = LoggerFactory.getLogger(DevicePtzExtensionTable.class);

    /**
     * 创建设备PTZ扩展表
     */
    public static void createTables(Connection connection) throws SQLException {
        String createDevicePtzExtensionTable = "CREATE TABLE IF NOT EXISTS device_ptz_extension (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "device_id TEXT NOT NULL UNIQUE, " +
            "is_ptz_enabled INTEGER DEFAULT 0, " +      // 是否启用PTZ信息获取
            "pan REAL DEFAULT 0.0, " +                  // 水平角度
            "tilt REAL DEFAULT 0.0, " +                 // 垂直角度
            "zoom REAL DEFAULT 1.0, " +                 // 变倍
            "azimuth REAL DEFAULT 0.0, " +              // 方位角
            "horizontal_fov REAL DEFAULT 0.0, " +       // 水平视场角
            "vertical_fov REAL DEFAULT 0.0, " +         // 垂直视场角
            "visible_radius REAL DEFAULT 0.0, " +       // 可视半径
            "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ")";

        String createIndex = "CREATE INDEX IF NOT EXISTS idx_ptz_ext_device_id ON device_ptz_extension(device_id); " +
            "CREATE INDEX IF NOT EXISTS idx_ptz_ext_enabled ON device_ptz_extension(is_ptz_enabled);";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createDevicePtzExtensionTable);
            stmt.execute(createIndex);
            logger.info("设备PTZ扩展表创建成功");
        }
    }

    /**
     * PTZ扩展信息实体类
     */
    public static class PtzExtension {
        private int id;
        private String deviceId;
        private boolean ptzEnabled;
        private float pan;
        private float tilt;
        private float zoom;
        private float azimuth;
        private float horizontalFov;
        private float verticalFov;
        private float visibleRadius;
        private Timestamp lastUpdated;

        public PtzExtension() {}

        public PtzExtension(String deviceId) {
            this.deviceId = deviceId;
            this.ptzEnabled = false;
            this.pan = 0.0f;
            this.tilt = 0.0f;
            this.zoom = 1.0f;
            this.azimuth = 0.0f;
            this.horizontalFov = 0.0f;
            this.verticalFov = 0.0f;
            this.visibleRadius = 0.0f;
        }

        // Getters and Setters
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }

        public String getDeviceId() { return deviceId; }
        public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

        public boolean isPtzEnabled() { return ptzEnabled; }
        public void setPtzEnabled(boolean ptzEnabled) { this.ptzEnabled = ptzEnabled; }

        public float getPan() { return pan; }
        public void setPan(float pan) { this.pan = pan; }

        public float getTilt() { return tilt; }
        public void setTilt(float tilt) { this.tilt = tilt; }

        public float getZoom() { return zoom; }
        public void setZoom(float zoom) { this.zoom = zoom; }

        public float getAzimuth() { return azimuth; }
        public void setAzimuth(float azimuth) { this.azimuth = azimuth; }

        public float getHorizontalFov() { return horizontalFov; }
        public void setHorizontalFov(float horizontalFov) { this.horizontalFov = horizontalFov; }

        public float getVerticalFov() { return verticalFov; }
        public void setVerticalFov(float verticalFov) { this.verticalFov = verticalFov; }

        public float getVisibleRadius() { return visibleRadius; }
        public void setVisibleRadius(float visibleRadius) { this.visibleRadius = visibleRadius; }

        public Timestamp getLastUpdated() { return lastUpdated; }
        public void setLastUpdated(Timestamp lastUpdated) { this.lastUpdated = lastUpdated; }
    }

    /**
     * 保存或更新PTZ扩展信息
     */
    public static boolean saveOrUpdate(Connection connection, PtzExtension ext) {
        String sql = "INSERT OR REPLACE INTO device_ptz_extension " +
            "(device_id, is_ptz_enabled, pan, tilt, zoom, azimuth, horizontal_fov, vertical_fov, visible_radius, last_updated) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, ext.getDeviceId());
            pstmt.setInt(2, ext.isPtzEnabled() ? 1 : 0);
            pstmt.setFloat(3, ext.getPan());
            pstmt.setFloat(4, ext.getTilt());
            pstmt.setFloat(5, ext.getZoom());
            pstmt.setFloat(6, ext.getAzimuth());
            pstmt.setFloat(7, ext.getHorizontalFov());
            pstmt.setFloat(8, ext.getVerticalFov());
            pstmt.setFloat(9, ext.getVisibleRadius());
            pstmt.executeUpdate();
            logger.debug("PTZ扩展信息已保存: {}", ext.getDeviceId());
            return true;
        } catch (SQLException e) {
            logger.error("保存PTZ扩展信息失败: {}", ext.getDeviceId(), e);
            return false;
        }
    }

    /**
     * 根据设备ID获取PTZ扩展信息
     */
    public static PtzExtension getByDeviceId(Connection connection, String deviceId) {
        String sql = "SELECT * FROM device_ptz_extension WHERE device_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, deviceId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return mapResultSet(rs);
            }
        } catch (SQLException e) {
            logger.error("查询PTZ扩展信息失败: {}", deviceId, e);
        }
        return null;
    }

    /**
     * 获取所有启用PTZ监控的设备
     */
    public static List<PtzExtension> getAllEnabled(Connection connection) {
        List<PtzExtension> list = new ArrayList<>();
        String sql = "SELECT * FROM device_ptz_extension WHERE is_ptz_enabled = 1";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            logger.error("查询启用PTZ监控的设备失败", e);
        }
        return list;
    }

    /**
     * 获取所有PTZ扩展记录
     */
    public static List<PtzExtension> getAll(Connection connection) {
        List<PtzExtension> list = new ArrayList<>();
        String sql = "SELECT * FROM device_ptz_extension ORDER BY device_id";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            logger.error("查询所有PTZ扩展信息失败", e);
        }
        return list;
    }

    /**
     * 更新PTZ位置信息
     */
    public static boolean updatePtzPosition(Connection connection, String deviceId, 
            float pan, float tilt, float zoom, float azimuth, 
            float horizontalFov, float verticalFov, float visibleRadius) {
        String sql = "UPDATE device_ptz_extension SET " +
            "pan = ?, tilt = ?, zoom = ?, azimuth = ?, " +
            "horizontal_fov = ?, vertical_fov = ?, visible_radius = ?, " +
            "last_updated = CURRENT_TIMESTAMP " +
            "WHERE device_id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setFloat(1, pan);
            pstmt.setFloat(2, tilt);
            pstmt.setFloat(3, zoom);
            pstmt.setFloat(4, azimuth);
            pstmt.setFloat(5, horizontalFov);
            pstmt.setFloat(6, verticalFov);
            pstmt.setFloat(7, visibleRadius);
            pstmt.setString(8, deviceId);
            int rows = pstmt.executeUpdate();
            if (rows > 0) {
                logger.debug("PTZ位置信息已更新: {} -> pan={}, tilt={}, zoom={}", deviceId, pan, tilt, zoom);
                return true;
            }
        } catch (SQLException e) {
            logger.error("更新PTZ位置信息失败: {}", deviceId, e);
        }
        return false;
    }

    /**
     * 设置PTZ监控开关
     */
    public static boolean setPtzEnabled(Connection connection, String deviceId, boolean enabled) {
        // 先检查记录是否存在
        PtzExtension ext = getByDeviceId(connection, deviceId);
        if (ext == null) {
            // 不存在则创建新记录
            ext = new PtzExtension(deviceId);
            ext.setPtzEnabled(enabled);
            return saveOrUpdate(connection, ext);
        }

        // 存在则更新
        String sql = "UPDATE device_ptz_extension SET is_ptz_enabled = ?, last_updated = CURRENT_TIMESTAMP WHERE device_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, enabled ? 1 : 0);
            pstmt.setString(2, deviceId);
            int rows = pstmt.executeUpdate();
            if (rows > 0) {
                logger.info("PTZ监控状态已更新: {} -> {}", deviceId, enabled ? "启用" : "禁用");
                return true;
            }
        } catch (SQLException e) {
            logger.error("更新PTZ监控状态失败: {}", deviceId, e);
        }
        return false;
    }

    /**
     * 删除PTZ扩展记录
     */
    public static boolean delete(Connection connection, String deviceId) {
        String sql = "DELETE FROM device_ptz_extension WHERE device_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, deviceId);
            int rows = pstmt.executeUpdate();
            if (rows > 0) {
                logger.info("PTZ扩展记录已删除: {}", deviceId);
                return true;
            }
        } catch (SQLException e) {
            logger.error("删除PTZ扩展记录失败: {}", deviceId, e);
        }
        return false;
    }

    /**
     * 映射结果集到实体
     */
    private static PtzExtension mapResultSet(ResultSet rs) throws SQLException {
        PtzExtension ext = new PtzExtension();
        ext.setId(rs.getInt("id"));
        ext.setDeviceId(rs.getString("device_id"));
        ext.setPtzEnabled(rs.getInt("is_ptz_enabled") == 1);
        ext.setPan(rs.getFloat("pan"));
        ext.setTilt(rs.getFloat("tilt"));
        ext.setZoom(rs.getFloat("zoom"));
        ext.setAzimuth(rs.getFloat("azimuth"));
        ext.setHorizontalFov(rs.getFloat("horizontal_fov"));
        ext.setVerticalFov(rs.getFloat("vertical_fov"));
        ext.setVisibleRadius(rs.getFloat("visible_radius"));
        ext.setLastUpdated(rs.getTimestamp("last_updated"));
        return ext;
    }
}
