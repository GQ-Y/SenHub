package com.hikvision.nvr.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite数据库管理类
 * 用于存储和管理扫描到的设备信息
 */
public class Database {
    private static final Logger logger = LoggerFactory.getLogger(Database.class);
    private String dbPath;
    private Connection connection;

    public Database(String dbPath) {
        this.dbPath = dbPath;
    }

    /**
     * 初始化数据库，创建表结构
     */
    public boolean init() {
        try {
            // 确保数据库目录存在
            java.io.File dbFile = new java.io.File(dbPath);
            java.io.File parentDir = dbFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // 连接数据库
            String url = "jdbc:sqlite:" + dbPath;
            connection = DriverManager.getConnection(url);
            logger.info("数据库连接成功: {}", dbPath);

            // 创建表
            createTables();
            return true;
        } catch (SQLException e) {
            logger.error("数据库初始化失败", e);
            return false;
        }
    }

    /**
     * 创建数据库表
     */
    private void createTables() throws SQLException {
        String createDevicesTable = "CREATE TABLE IF NOT EXISTS devices (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "device_id TEXT UNIQUE NOT NULL, " +
            "ip TEXT NOT NULL, " +
            "port INTEGER NOT NULL, " +
            "name TEXT, " +
            "username TEXT, " +
            "password TEXT, " +
            "rtsp_url TEXT, " +
            "status TEXT DEFAULT 'offline', " +
            "user_id INTEGER DEFAULT -1, " +
            "channel INTEGER DEFAULT 1, " +
            "last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
            "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ")";

        String createIndex = "CREATE INDEX IF NOT EXISTS idx_device_id ON devices(device_id); " +
            "CREATE INDEX IF NOT EXISTS idx_ip_port ON devices(ip, port);";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createDevicesTable);
            stmt.execute(createIndex);
            
            // 检查并添加channel列（如果表已存在但缺少该列）
            try {
                stmt.executeQuery("SELECT channel FROM devices LIMIT 1");
            } catch (SQLException e) {
                // 如果查询失败，说明缺少channel列，需要添加
                logger.info("检测到缺少channel列，正在添加...");
                try {
                    stmt.execute("ALTER TABLE devices ADD COLUMN channel INTEGER DEFAULT 1");
                    logger.info("成功添加channel列");
                } catch (SQLException ex) {
                    // 如果添加失败（可能列已存在），忽略错误
                    logger.debug("添加channel列失败（可能已存在）: {}", ex.getMessage());
                }
            }
            
            logger.info("数据库表创建成功");
        }
    }

    /**
     * 保存或更新设备信息
     */
    public boolean saveOrUpdateDevice(DeviceInfo device) {
        String sql = "INSERT OR REPLACE INTO devices " +
            "(device_id, ip, port, name, username, password, rtsp_url, status, user_id, channel, last_seen, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, device.getDeviceId());
            pstmt.setString(2, device.getIp());
            pstmt.setInt(3, device.getPort());
            pstmt.setString(4, device.getName());
            pstmt.setString(5, device.getUsername());
            pstmt.setString(6, device.getPassword());
            pstmt.setString(7, device.getRtspUrl());
            pstmt.setString(8, device.getStatus());
            pstmt.setInt(9, device.getUserId());
            pstmt.setInt(10, device.getChannel() > 0 ? device.getChannel() : 1);

            pstmt.executeUpdate();
            logger.debug("设备信息已保存: {}:{}", device.getIp(), device.getPort());
            return true;
        } catch (SQLException e) {
            logger.error("保存设备信息失败", e);
            return false;
        }
    }

    /**
     * 根据设备ID获取设备信息
     */
    public DeviceInfo getDevice(String deviceId) {
        String sql = "SELECT * FROM devices WHERE device_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, deviceId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return mapResultSetToDevice(rs);
            }
        } catch (SQLException e) {
            logger.error("查询设备信息失败: {}", deviceId, e);
        }
        return null;
    }

    /**
     * 根据IP和端口获取设备信息
     */
    public DeviceInfo getDeviceByIpPort(String ip, int port) {
        String sql = "SELECT * FROM devices WHERE ip = ? AND port = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, ip);
            pstmt.setInt(2, port);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return mapResultSetToDevice(rs);
            }
        } catch (SQLException e) {
            logger.error("查询设备信息失败: {}:{}", ip, port, e);
        }
        return null;
    }

    /**
     * 获取所有设备
     */
    public List<DeviceInfo> getAllDevices() {
        List<DeviceInfo> devices = new ArrayList<>();
        String sql = "SELECT * FROM devices ORDER BY created_at DESC";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                devices.add(mapResultSetToDevice(rs));
            }
        } catch (SQLException e) {
            logger.error("查询所有设备失败", e);
        }
        return devices;
    }

    /**
     * 更新设备状态
     */
    public boolean updateDeviceStatus(String deviceId, String status, int userId) {
        String sql = "UPDATE devices SET status = ?, user_id = ?, updated_at = CURRENT_TIMESTAMP WHERE device_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, status);
            pstmt.setInt(2, userId);
            pstmt.setString(3, deviceId);
            int rows = pstmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            logger.error("更新设备状态失败: {}", deviceId, e);
            return false;
        }
    }

    /**
     * 更新设备最后发现时间
     */
    public boolean updateLastSeen(String deviceId) {
        String sql = "UPDATE devices SET last_seen = CURRENT_TIMESTAMP WHERE device_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, deviceId);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.error("更新设备最后发现时间失败: {}", deviceId, e);
            return false;
        }
    }

    /**
     * 删除设备
     */
    public boolean deleteDevice(String deviceId) {
        String sql = "DELETE FROM devices WHERE device_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, deviceId);
            int rows = pstmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            logger.error("删除设备失败: {}", deviceId, e);
            return false;
        }
    }

    /**
     * 将ResultSet映射为DeviceInfo对象
     */
    private DeviceInfo mapResultSetToDevice(ResultSet rs) throws SQLException {
        DeviceInfo device = new DeviceInfo();
        device.setId(rs.getInt("id"));
        device.setDeviceId(rs.getString("device_id"));
        device.setIp(rs.getString("ip"));
        device.setPort(rs.getInt("port"));
        device.setName(rs.getString("name"));
        device.setUsername(rs.getString("username"));
        device.setPassword(rs.getString("password"));
        device.setRtspUrl(rs.getString("rtsp_url"));
        device.setStatus(rs.getString("status"));
        device.setUserId(rs.getInt("user_id"));
        device.setChannel(rs.getInt("channel"));
        device.setLastSeen(rs.getTimestamp("last_seen"));
        device.setCreatedAt(rs.getTimestamp("created_at"));
        device.setUpdatedAt(rs.getTimestamp("updated_at"));
        return device;
    }

    /**
     * 关闭数据库连接
     */
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                logger.info("数据库连接已关闭");
            }
        } catch (SQLException e) {
            logger.error("关闭数据库连接失败", e);
        }
    }
}
