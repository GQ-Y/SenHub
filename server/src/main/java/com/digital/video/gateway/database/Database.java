package com.digital.video.gateway.database;

import com.digital.video.gateway.Common.LibraryPathHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

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
                "status INTEGER DEFAULT 0, " + // 0: offline, 1: online
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

        String createNotificationsTable = "CREATE TABLE IF NOT EXISTS notifications (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "title TEXT NOT NULL, " +
                "message TEXT NOT NULL, " +
                "type TEXT DEFAULT 'info', " +
                "read INTEGER DEFAULT 0, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")";

        String createIndex = "CREATE INDEX IF NOT EXISTS idx_device_id ON devices(device_id); " +
                "CREATE INDEX IF NOT EXISTS idx_ip_port ON devices(ip, port); " +
                "CREATE INDEX IF NOT EXISTS idx_brand ON devices(brand); " +
                "CREATE INDEX IF NOT EXISTS idx_config_key ON configs(config_key); " +
                "CREATE INDEX IF NOT EXISTS idx_driver_id ON drivers(driver_id); " +
                "CREATE INDEX IF NOT EXISTS idx_device_history_device_id ON device_history(device_id); " +
                "CREATE INDEX IF NOT EXISTS idx_device_history_recorded_at ON device_history(recorded_at); " +
                "CREATE INDEX IF NOT EXISTS idx_alarm_history_recorded_at ON alarm_history(recorded_at); " +
                "CREATE INDEX IF NOT EXISTS idx_notifications_read ON notifications(read); " +
                "CREATE INDEX IF NOT EXISTS idx_notifications_created_at ON notifications(created_at);";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createDevicesTable);
            stmt.execute(createUsersTable);
            stmt.execute(createConfigsTable);
            stmt.execute(createDriversTable);
            stmt.execute(createDeviceHistoryTable);
            stmt.execute(createAlarmHistoryTable);
            stmt.execute(createNotificationsTable);
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

            // 创建新增的表
            AssemblyTable.createTables(connection);
            AlarmRuleTable.createTables(connection);
            AlarmRecordTable.createTables(connection);
            AlarmFlowTable.createTables(connection);
            SpeakerTable.createTables(connection);
            RecordingTaskTable.createTables(connection);

            // 创建雷达相关表
            RadarDeviceTable.createTables(connection);
            RadarBackgroundTable.createTables(connection);
            RadarDefenseZoneTable.createTables(connection);

            // 创建摄像头事件类型表
            CameraEventTypeTable.createTables(connection);
            
            // 创建标准事件表和品牌映射表
            CanonicalEventTable.createTables(connection);

            logger.info("数据库表创建成功");
        }
    }

    /**
     * 保存或更新设备信息
     */
    public boolean saveOrUpdateDevice(DeviceInfo device) {
        String sql = "INSERT OR REPLACE INTO devices " +
                "(device_id, ip, port, name, username, password, rtsp_url, status, user_id, channel, brand, last_seen, updated_at) "
                +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, device.getDeviceId());
            pstmt.setString(2, device.getIp());
            pstmt.setInt(3, device.getPort());
            pstmt.setString(4, device.getName());
            pstmt.setString(5, device.getUsername());
            pstmt.setString(6, device.getPassword());
            pstmt.setString(7, device.getRtspUrl());
            pstmt.setInt(8, device.getStatus());
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
    public boolean updateDeviceStatus(String deviceId, int status, int userId) {
        String sql = "UPDATE devices SET status = ?, user_id = ?, updated_at = CURRENT_TIMESTAMP WHERE device_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, status);
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
            String passwordHash = org.mindrot.jbcrypt.BCrypt.hashpw(defaultPassword,
                    org.mindrot.jbcrypt.BCrypt.gensalt());
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
     * 检测实际的SDK库文件路径
     * @param sdkName SDK名称：hikvision, tiandy, dahua, livox
     * @return 检测到的库目录路径，如果未找到则返回null
     */
    private String detectActualSDKLibPath(String sdkName) {
        String userDir = System.getProperty("user.dir");
        String archDir = LibraryPathHelper.getArchitectureDir();
        String osName = System.getProperty("os.name").toLowerCase();
        String osType = (osName.contains("mac") || osName.contains("darwin")) ? "macos" : "linux";
        
        // 定义每个SDK的库文件名
        String[] libraryFileNames;
        String[] searchPaths;
        
        if ("livox".equalsIgnoreCase(sdkName)) {
            // Livox SDK使用操作系统类型路径
            String libExt = osType.contains("mac") ? ".dylib" : ".so";
            libraryFileNames = new String[]{"liblivoxjni" + libExt};
            searchPaths = new String[]{
                userDir + "/lib/" + osType,
                userDir + "/lib/linux",
                userDir + "/lib/macos"
            };
        } else {
            // 其他SDK使用架构路径
            if ("hikvision".equalsIgnoreCase(sdkName)) {
                libraryFileNames = new String[]{"libhcnetsdk.so", "libhcnetsdk.so.x86_64", "libhcnetsdk.so.aarch64"};
            } else if ("tiandy".equalsIgnoreCase(sdkName)) {
                libraryFileNames = new String[]{"libnvssdk.so", "libnvssdk.so.x86_64"};
            } else if ("dahua".equalsIgnoreCase(sdkName)) {
                libraryFileNames = new String[]{"libdhnetsdk.so", "libdhnetsdk.so.x86_64", "libdhnetsdk.so.aarch64"};
            } else {
                return null;
            }
            
            // 构建搜索路径列表
            searchPaths = new String[]{
                userDir + "/lib/" + archDir + "/" + sdkName,
                userDir + "/lib/x86/" + sdkName,
                userDir + "/lib/arm/" + sdkName,
                userDir + "/lib/" + sdkName
            };
        }
        
        // 遍历搜索路径，查找库文件
        for (String searchPath : searchPaths) {
            File searchDir = new File(searchPath);
            if (searchDir.exists() && searchDir.isDirectory()) {
                for (String libFileName : libraryFileNames) {
                    File libFile = new File(searchDir, libFileName);
                    if (libFile.exists() && libFile.isFile()) {
                        logger.info("检测到{} SDK库文件: {}", sdkName, searchPath);
                        return searchPath.replace(userDir + "/", "./");
                    }
                }
                // 也检查目录中是否有任何.so或.dylib文件（作为备选）
                File[] files = searchDir.listFiles((dir, name) -> 
                    name.endsWith(".so") || name.endsWith(".dylib"));
                if (files != null && files.length > 0) {
                    logger.info("在目录中找到库文件，使用路径: {}", searchPath);
                    return searchPath.replace(userDir + "/", "./");
                }
            }
        }
        
        logger.warn("未找到{} SDK库文件，使用默认路径", sdkName);
        return null;
    }

    /**
     * 初始化默认SDK驱动配置
     */
    private void initDefaultDriver() {
        String userDir = System.getProperty("user.dir");
        String archDir = LibraryPathHelper.getArchitectureDir();
        String osName = System.getProperty("os.name").toLowerCase();
        String osType = (osName.contains("mac") || osName.contains("darwin")) ? "macos" : "linux";
        
        // 初始化海康威视SDK - 检测实际路径
        String hikvisionPath = detectActualSDKLibPath("hikvision");
        if (hikvisionPath == null) {
            // 如果检测不到，使用默认路径
            String defaultPath = LibraryPathHelper.getSDKLibPath("hikvision");
            hikvisionPath = defaultPath != null ? defaultPath.replace(userDir + "/", "./") : "./lib/" + archDir + "/hikvision";
            logger.warn("海康SDK使用默认路径: {}", hikvisionPath);
        }
        initDefaultDriverWithConfig(
                "hikvision_sdk",
                "Hikvision SDK",
                "6.1.9.45",
                hikvisionPath,
                "./sdkLog",
                3,
                "ACTIVE");
        
        // 初始化天地伟业SDK - 仅x86架构，检测实际路径
        if ("x86".equals(archDir)) {
            String tiandyPath = detectActualSDKLibPath("tiandy");
            if (tiandyPath == null) {
                String defaultPath = LibraryPathHelper.getSDKLibPath("tiandy");
                tiandyPath = defaultPath != null ? defaultPath.replace(userDir + "/", "./") : "./lib/x86/tiandy";
            }
            initDefaultDriverWithConfig(
                    "tiandy_sdk",
                    "Tiandy SDK",
                    "1.0.0",
                    tiandyPath,
                    "./sdkLog",
                    3,
                    "ACTIVE");
        }
        
        // 初始化大华SDK - 检测实际路径
        String dahuaPath = detectActualSDKLibPath("dahua");
        if (dahuaPath == null) {
            String defaultPath = LibraryPathHelper.getSDKLibPath("dahua");
            dahuaPath = defaultPath != null ? defaultPath.replace(userDir + "/", "./") : "./lib/" + archDir + "/dahua";
        }
        initDefaultDriverWithConfig(
                "dahua_sdk",
                "Dahua SDK",
                "1.0.0",
                dahuaPath,
                "./sdkLog",
                3,
                "ACTIVE");
        
        // 初始化雷达SDK (Livox) - 检测实际路径
        String livoxPath = detectActualSDKLibPath("livox");
        if (livoxPath == null) {
            String defaultPath = LibraryPathHelper.getLivoxLibPath(osType);
            livoxPath = defaultPath.replace(userDir + "/", "./");
        }
        initDefaultDriverWithConfig(
                "livox_sdk",
                "Livox Radar SDK",
                "1.0.0",
                livoxPath,
                "./sdkLog",
                3,
                "ACTIVE");
    }

    /**
     * 使用指定配置初始化默认SDK驱动
     * 
     * @param driverId 驱动ID
     * @param name     驱动名称
     * @param version  版本号
     * @param libPath  SDK库路径
     * @param logPath  日志路径
     * @param logLevel 日志级别
     * @param status   状态
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
        device.setStatus(rs.getInt("status"));
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
    public boolean saveOrUpdateDriver(String driverId, String name, String version, String libPath, String logPath,
            int logLevel, String status) {
        String sql = "INSERT OR REPLACE INTO drivers (driver_id, name, version, lib_path, log_path, log_level, status, updated_at) "
                +
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
    public boolean recordDeviceHistory(String deviceId, int status) {
        String sql = "INSERT INTO device_history (device_id, status, recorded_at) VALUES (?, ?, CURRENT_TIMESTAMP)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, deviceId);
            pstmt.setInt(2, status);
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
                "COUNT(DISTINCT CASE WHEN status = 1 THEN device_id END) as online_count " +
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
            String[] timePoints = { "00", "04", "08", "12", "16", "20", "24" };
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
            int deleted1 = stmt
                    .executeUpdate("DELETE FROM device_history WHERE recorded_at < datetime('now', '-30 days')");
            int deleted2 = stmt
                    .executeUpdate("DELETE FROM alarm_history WHERE recorded_at < datetime('now', '-30 days')");
            int deleted3 = stmt
                    .executeUpdate("DELETE FROM notifications WHERE created_at < datetime('now', '-30 days')");
            if (deleted1 > 0 || deleted2 > 0 || deleted3 > 0) {
                logger.info("清理旧历史数据完成: device_history={}, alarm_history={}, notifications={}", deleted1, deleted2, deleted3);
            }
        } catch (SQLException e) {
            logger.error("清理旧历史数据失败", e);
        }
    }

    // ==================== 通知管理方法 ====================

    /**
     * 创建通知
     */
    public boolean createNotification(String title, String message, String type) {
        String sql = "INSERT INTO notifications (title, message, type, read, created_at) VALUES (?, ?, ?, 0, CURRENT_TIMESTAMP)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, title);
            pstmt.setString(2, message);
            pstmt.setString(3, type != null ? type : "info");
            pstmt.executeUpdate();
            logger.debug("通知已创建: {}", title);
            return true;
        } catch (SQLException e) {
            logger.error("创建通知失败", e);
            return false;
        }
    }

    /**
     * 获取所有通知（按创建时间倒序）
     */
    public List<Map<String, Object>> getAllNotifications(int limit) {
        List<Map<String, Object>> notifications = new ArrayList<>();
        String sql = "SELECT * FROM notifications ORDER BY created_at DESC LIMIT ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, limit > 0 ? limit : 100);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> notification = new HashMap<>();
                notification.put("id", String.valueOf(rs.getInt("id")));
                notification.put("title", rs.getString("title"));
                notification.put("message", rs.getString("message"));
                notification.put("type", rs.getString("type"));
                notification.put("read", rs.getInt("read") == 1);
                notification.put("time", formatTimeAgo(rs.getTimestamp("created_at")));
                notifications.add(notification);
            }
        } catch (SQLException e) {
            logger.error("获取通知列表失败", e);
        }
        return notifications;
    }

    /**
     * 标记通知为已读
     */
    public boolean markNotificationAsRead(String notificationId) {
        String sql = "UPDATE notifications SET read = 1 WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(notificationId));
            int rows = pstmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            logger.error("标记通知为已读失败: {}", notificationId, e);
            return false;
        }
    }

    /**
     * 标记所有通知为已读
     */
    public boolean markAllNotificationsAsRead() {
        String sql = "UPDATE notifications SET read = 1 WHERE read = 0";
        try (Statement stmt = connection.createStatement()) {
            int rows = stmt.executeUpdate(sql);
            logger.info("已标记 {} 条通知为已读", rows);
            return true;
        } catch (SQLException e) {
            logger.error("标记所有通知为已读失败", e);
            return false;
        }
    }

    /**
     * 格式化时间为相对时间（如"2分钟前"）
     */
    private String formatTimeAgo(java.sql.Timestamp timestamp) {
        if (timestamp == null) return "未知";
        long now = System.currentTimeMillis();
        long time = timestamp.getTime();
        long diff = now - time;
        
        if (diff < 60000) { // 小于1分钟
            return "刚刚";
        } else if (diff < 3600000) { // 小于1小时
            return (diff / 60000) + "分钟前";
        } else if (diff < 86400000) { // 小于1天
            return (diff / 3600000) + "小时前";
        } else {
            return (diff / 86400000) + "天前";
        }
    }

    /**
     * 获取数据库连接（返回代理连接，防止被误关闭）
     */
    public Connection getConnection() {
        if (connection == null)
            return null;

        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        // 如果调用的是 close 方法，且连接还没有真正关闭，则忽略
                        if ("close".equals(method.getName())) {
                            logger.debug("忽略对共享数据库连接的 close() 调用");
                            return null;
                        }
                        // 其他方法正常执行
                        try {
                            return method.invoke(connection, args);
                        } catch (java.lang.reflect.InvocationTargetException e) {
                            throw e.getTargetException();
                        }
                    }
                });
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
