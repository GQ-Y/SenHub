package com.hikvision.nvr.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            // 初始化默认管理员用户
            initDefaultUser();
            // 初始化默认SDK驱动配置
            initDefaultDriver();
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
            "brand TEXT DEFAULT 'auto', " +
            "last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
            "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ")";

        String createUsersTable = "CREATE TABLE IF NOT EXISTS users (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "username TEXT UNIQUE NOT NULL, " +
            "password TEXT NOT NULL, " +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
            "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ")";

        String createConfigsTable = "CREATE TABLE IF NOT EXISTS configs (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "config_key TEXT UNIQUE NOT NULL, " +
            "config_value TEXT, " +
            "config_type TEXT DEFAULT 'string', " +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
            "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ")";

        String createDriversTable = "CREATE TABLE IF NOT EXISTS drivers (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "driver_id TEXT UNIQUE NOT NULL, " +
            "name TEXT NOT NULL, " +
            "version TEXT, " +
            "lib_path TEXT, " +
            "log_path TEXT, " +
            "log_level INTEGER DEFAULT 1, " +
            "status TEXT DEFAULT 'INACTIVE', " +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
            "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ")";

        String createDeviceHistoryTable = "CREATE TABLE IF NOT EXISTS device_history (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "device_id TEXT NOT NULL, " +
            "status TEXT NOT NULL, " +
            "recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ")";

        String createAlarmHistoryTable = "CREATE TABLE IF NOT EXISTS alarm_history (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "device_id TEXT, " +
            "alarm_type TEXT NOT NULL, " +
            "alarm_level TEXT DEFAULT 'warning', " +
            "message TEXT, " +
            "recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ")";

        String createIndex = "CREATE INDEX IF NOT EXISTS idx_device_id ON devices(device_id); " +
            "CREATE INDEX IF NOT EXISTS idx_ip_port ON devices(ip, port); " +
            "CREATE INDEX IF NOT EXISTS idx_brand ON devices(brand); " +
            "CREATE INDEX IF NOT EXISTS idx_config_key ON configs(config_key); " +
            "CREATE INDEX IF NOT EXISTS idx_driver_id ON drivers(driver_id); " +
            "CREATE INDEX IF NOT EXISTS idx_device_history_device_id ON device_history(device_id); " +
            "CREATE INDEX IF NOT EXISTS idx_device_history_recorded_at ON device_history(recorded_at); " +
            "CREATE INDEX IF NOT EXISTS idx_alarm_history_recorded_at ON alarm_history(recorded_at);";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createDevicesTable);
            stmt.execute(createUsersTable);
            stmt.execute(createConfigsTable);
            stmt.execute(createDriversTable);
            stmt.execute(createDeviceHistoryTable);
            stmt.execute(createAlarmHistoryTable);
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
            
            // 检查并添加brand列（如果表已存在但缺少该列）
            try {
                stmt.executeQuery("SELECT brand FROM devices LIMIT 1");
            } catch (SQLException e) {
                // 如果查询失败，说明缺少brand列，需要添加
                logger.info("检测到缺少brand列，正在添加...");
                try {
                    stmt.execute("ALTER TABLE devices ADD COLUMN brand TEXT DEFAULT 'auto'");
                    logger.info("成功添加brand列");
                } catch (SQLException ex) {
                    // 如果添加失败（可能列已存在），忽略错误
                    logger.debug("添加brand列失败（可能已存在）: {}", ex.getMessage());
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
            "(device_id, ip, port, name, username, password, rtsp_url, status, user_id, channel, brand, last_seen, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";

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
            pstmt.setString(11, device.getBrand() != null ? device.getBrand() : "auto");

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
            
            // 记录设备状态历史
            if (rows > 0) {
                recordDeviceHistory(deviceId, status);
            }
            
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
     * 初始化默认管理员用户
     */
    private void initDefaultUser() {
        String defaultUsername = "admin";
        String defaultPassword = "123456";
        
        if (!userExists(defaultUsername)) {
            // 使用BCrypt加密密码
            String passwordHash = org.mindrot.jbcrypt.BCrypt.hashpw(defaultPassword, org.mindrot.jbcrypt.BCrypt.gensalt());
            if (createUser(defaultUsername, passwordHash)) {
                logger.info("默认管理员用户已创建: {}", defaultUsername);
            } else {
                logger.error("创建默认管理员用户失败");
            }
        } else {
            logger.debug("默认管理员用户已存在");
        }
    }

    /**
     * 初始化默认SDK驱动配置
     */
    private void initDefaultDriver() {
        // 使用默认配置值（SDK库已复制到项目目录）
        initDefaultDriverWithConfig(
            "hikvision_sdk",
            "Hikvision SDK",
            "6.1.9.45",
            "./lib/hikvision",
            "./sdkLog",
            3,
            "ACTIVE"
        );
    }

    /**
     * 使用指定配置初始化默认SDK驱动
     * @param driverId 驱动ID
     * @param name 驱动名称
     * @param version 版本号
     * @param libPath SDK库路径
     * @param logPath 日志路径
     * @param logLevel 日志级别
     * @param status 状态
     */
    public void initDefaultDriverWithConfig(String driverId, String name, String version, 
                                           String libPath, String logPath, int logLevel, String status) {
        // 检查驱动是否已存在
        if (getDriver(driverId) == null) {
            if (saveOrUpdateDriver(driverId, name, version, libPath, logPath, logLevel, status)) {
                logger.info("默认SDK驱动配置已创建: {} - {}", driverId, name);
            } else {
                logger.error("创建默认SDK驱动配置失败: {}", driverId);
            }
        } else {
            logger.debug("默认SDK驱动配置已存在: {}", driverId);
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
        // 处理brand字段，如果不存在则使用默认值
        try {
            device.setBrand(rs.getString("brand"));
        } catch (SQLException e) {
            device.setBrand("auto");
        }
        device.setLastSeen(rs.getTimestamp("last_seen"));
        device.setCreatedAt(rs.getTimestamp("created_at"));
        device.setUpdatedAt(rs.getTimestamp("updated_at"));
        return device;
    }

    // ==================== 用户管理方法 ====================

    /**
     * 创建用户
     */
    public boolean createUser(String username, String passwordHash) {
        String sql = "INSERT INTO users (username, password) VALUES (?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, passwordHash);
            pstmt.executeUpdate();
            logger.info("用户创建成功: {}", username);
            return true;
        } catch (SQLException e) {
            logger.error("创建用户失败: {}", username, e);
            return false;
        }
    }

    /**
     * 根据用户名获取用户密码哈希
     */
    public String getUserPasswordHash(String username) {
        String sql = "SELECT password FROM users WHERE username = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("password");
            }
        } catch (SQLException e) {
            logger.error("查询用户密码失败: {}", username, e);
        }
        return null;
    }

    /**
     * 检查用户是否存在
     */
    public boolean userExists(String username) {
        String sql = "SELECT COUNT(*) FROM users WHERE username = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            logger.error("检查用户是否存在失败: {}", username, e);
        }
        return false;
    }

    /**
     * 更新用户密码
     */
    public boolean updateUserPassword(String username, String passwordHash) {
        String sql = "UPDATE users SET password = ?, updated_at = CURRENT_TIMESTAMP WHERE username = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, passwordHash);
            pstmt.setString(2, username);
            int rows = pstmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            logger.error("更新用户密码失败: {}", username, e);
            return false;
        }
    }

    // ==================== 配置管理方法 ====================

    /**
     * 保存或更新配置
     */
    public boolean saveOrUpdateConfig(String key, String value, String type) {
        String sql = "INSERT OR REPLACE INTO configs (config_key, config_value, config_type, updated_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, key);
            pstmt.setString(2, value);
            pstmt.setString(3, type != null ? type : "string");
            pstmt.executeUpdate();
            logger.debug("配置已保存: {} = {}", key, value);
            return true;
        } catch (SQLException e) {
            logger.error("保存配置失败: {}", key, e);
            return false;
        }
    }

    /**
     * 获取配置值
     */
    public String getConfig(String key) {
        String sql = "SELECT config_value FROM configs WHERE config_key = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, key);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("config_value");
            }
        } catch (SQLException e) {
            logger.error("查询配置失败: {}", key, e);
        }
        return null;
    }

    /**
     * 获取所有配置
     */
    public java.util.Map<String, String> getAllConfigs() {
        java.util.Map<String, String> configs = new java.util.HashMap<>();
        String sql = "SELECT config_key, config_value FROM configs";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                configs.put(rs.getString("config_key"), rs.getString("config_value"));
            }
        } catch (SQLException e) {
            logger.error("查询所有配置失败", e);
        }
        return configs;
    }

    /**
     * 删除配置
     */
    public boolean deleteConfig(String key) {
        String sql = "DELETE FROM configs WHERE config_key = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, key);
            int rows = pstmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            logger.error("删除配置失败: {}", key, e);
            return false;
        }
    }

    // ==================== 驱动管理方法 ====================

    /**
     * 保存或更新驱动配置
     */
    public boolean saveOrUpdateDriver(String driverId, String name, String version, String libPath, String logPath, int logLevel, String status) {
        String sql = "INSERT OR REPLACE INTO drivers (driver_id, name, version, lib_path, log_path, log_level, status, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, driverId);
            pstmt.setString(2, name);
            pstmt.setString(3, version);
            pstmt.setString(4, libPath);
            pstmt.setString(5, logPath);
            pstmt.setInt(6, logLevel);
            pstmt.setString(7, status != null ? status : "INACTIVE");
            pstmt.executeUpdate();
            logger.debug("驱动配置已保存: {}", driverId);
            return true;
        } catch (SQLException e) {
            logger.error("保存驱动配置失败: {}", driverId, e);
            return false;
        }
    }

    /**
     * 获取驱动配置
     */
    public java.util.Map<String, Object> getDriver(String driverId) {
        String sql = "SELECT * FROM drivers WHERE driver_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, driverId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                java.util.Map<String, Object> driver = new java.util.HashMap<>();
                driver.put("id", rs.getString("driver_id"));
                driver.put("name", rs.getString("name"));
                driver.put("version", rs.getString("version"));
                driver.put("libPath", rs.getString("lib_path"));
                driver.put("logPath", rs.getString("log_path"));
                driver.put("logLevel", rs.getInt("log_level"));
                driver.put("status", rs.getString("status"));
                return driver;
            }
        } catch (SQLException e) {
            logger.error("查询驱动配置失败: {}", driverId, e);
        }
        return null;
    }

    /**
     * 获取所有驱动配置
     */
    public List<java.util.Map<String, Object>> getAllDrivers() {
        List<java.util.Map<String, Object>> drivers = new ArrayList<>();
        String sql = "SELECT * FROM drivers ORDER BY created_at DESC";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                java.util.Map<String, Object> driver = new java.util.HashMap<>();
                driver.put("id", rs.getString("driver_id"));
                driver.put("name", rs.getString("name"));
                driver.put("version", rs.getString("version"));
                driver.put("libPath", rs.getString("lib_path"));
                driver.put("logPath", rs.getString("log_path"));
                driver.put("logLevel", rs.getInt("log_level"));
                driver.put("status", rs.getString("status"));
                drivers.add(driver);
            }
        } catch (SQLException e) {
            logger.error("查询所有驱动配置失败", e);
        }
        return drivers;
    }

    /**
     * 删除驱动配置
     */
    public boolean deleteDriver(String driverId) {
        String sql = "DELETE FROM drivers WHERE driver_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, driverId);
            int rows = pstmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            logger.error("删除驱动配置失败: {}", driverId, e);
            return false;
        }
    }

    // ==================== 历史数据管理方法 ====================

    /**
     * 记录设备状态历史
     */
    public boolean recordDeviceHistory(String deviceId, String status) {
        String sql = "INSERT INTO device_history (device_id, status, recorded_at) VALUES (?, ?, CURRENT_TIMESTAMP)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, deviceId);
            pstmt.setString(2, status);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.error("记录设备状态历史失败: {}", deviceId, e);
            return false;
        }
    }

    /**
     * 记录告警历史
     */
    public boolean recordAlarm(String deviceId, String alarmType, String alarmLevel, String message) {
        String sql = "INSERT INTO alarm_history (device_id, alarm_type, alarm_level, message, recorded_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, deviceId);
            pstmt.setString(2, alarmType);
            pstmt.setString(3, alarmLevel != null ? alarmLevel : "warning");
            pstmt.setString(4, message);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.error("记录告警历史失败", e);
            return false;
        }
    }

    /**
     * 获取24小时内的设备连接趋势数据
     */
    public List<Map<String, Object>> getDeviceConnectivityTrend24h() {
        List<Map<String, Object>> trendData = new ArrayList<>();
        String sql = "SELECT " +
            "strftime('%H', recorded_at) as hour, " +
            "COUNT(DISTINCT CASE WHEN status = 'online' THEN device_id END) as online_count " +
            "FROM device_history " +
            "WHERE recorded_at >= datetime('now', '-24 hours') " +
            "GROUP BY strftime('%H', recorded_at) " +
            "ORDER BY hour";
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            Map<String, Integer> hourData = new HashMap<>();
            while (rs.next()) {
                String hour = rs.getString("hour");
                int onlineCount = rs.getInt("online_count");
                hourData.put(hour, onlineCount);
            }
            
            // 生成24小时数据点（每4小时一个点）
            String[] timePoints = {"00", "04", "08", "12", "16", "20", "24"};
            for (String time : timePoints) {
                Map<String, Object> dataPoint = new HashMap<>();
                dataPoint.put("name", time + ":00");
                int online = hourData.getOrDefault(time, 0);
                dataPoint.put("online", online);
                trendData.add(dataPoint);
            }
        } catch (SQLException e) {
            logger.error("获取设备连接趋势失败", e);
        }
        return trendData;
    }

    /**
     * 获取24小时内的告警数量
     */
    public int getAlarmCount24h() {
        String sql = "SELECT COUNT(*) as count FROM alarm_history WHERE recorded_at >= datetime('now', '-24 hours')";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt("count");
            }
        } catch (SQLException e) {
            logger.error("获取24小时告警数量失败", e);
        }
        return 0;
    }

    /**
     * 清理旧的历史数据（保留30天）
     */
    public void cleanupOldHistory() {
        try (Statement stmt = connection.createStatement()) {
            int deleted1 = stmt.executeUpdate("DELETE FROM device_history WHERE recorded_at < datetime('now', '-30 days')");
            int deleted2 = stmt.executeUpdate("DELETE FROM alarm_history WHERE recorded_at < datetime('now', '-30 days')");
            if (deleted1 > 0 || deleted2 > 0) {
                logger.info("清理旧历史数据完成: device_history={}, alarm_history={}", deleted1, deleted2);
            }
        } catch (SQLException e) {
            logger.error("清理旧历史数据失败", e);
        }
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
