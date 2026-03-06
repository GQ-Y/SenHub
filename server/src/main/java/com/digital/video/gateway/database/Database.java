package com.digital.video.gateway.database;

import com.digital.video.gateway.Common.LibraryPathHelper;
import com.digital.video.gateway.config.Config;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PostgreSQL 数据库管理类（HikariCP 连接池）
 */
public class Database {
    private static final Logger logger = LoggerFactory.getLogger(Database.class);

    private Config.DatabaseConfig dbConfig;
    private HikariDataSource dataSource;
    /** 兼容旧代码的粘性共享连接（不会被 close() 关闭） */
    private volatile Connection stickyConnection;

    /** MQTT 主题默认值 */
    private static final String DEFAULT_MQTT_STATUS_TOPIC = "senhub/device/status";
    private static final String DEFAULT_MQTT_COMMAND_TOPIC = "senhub/command";

    public Database(Config.DatabaseConfig dbConfig) {
        this.dbConfig = dbConfig;
    }

    /**
     * 初始化数据库连接池并创建表结构
     */
    public boolean init() {
        try {
            // 优先从环境变量读取连接参数（由 install.sh 写入 db.env，systemd 注入）
            String host = envOrDefault("DB_HOST", dbConfig != null ? dbConfig.getHost() : "127.0.0.1");
            String portStr = envOrDefault("DB_PORT", dbConfig != null ? String.valueOf(dbConfig.getPort()) : "5432");
            String dbName = envOrDefault("DB_NAME", dbConfig != null ? dbConfig.getName() : "senhub");
            String username = envOrDefault("DB_USER", dbConfig != null ? dbConfig.getUsername() : "senhub");
            String password = envOrDefault("DB_PASSWORD", dbConfig != null ? dbConfig.getPassword() : "");
            int poolSize = dbConfig != null ? dbConfig.getPoolSize() : 10;

            int port;
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                port = 5432;
            }

            String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s", host, port, dbName);

            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(jdbcUrl);
            hikariConfig.setUsername(username);
            hikariConfig.setPassword(password);
            hikariConfig.setMaximumPoolSize(poolSize);
            hikariConfig.setMinimumIdle(2);
            hikariConfig.setConnectionTimeout(30000);
            hikariConfig.setIdleTimeout(600000);
            hikariConfig.setMaxLifetime(1800000);
            hikariConfig.setPoolName("SenHubPool");
            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            dataSource = new HikariDataSource(hikariConfig);

            // 验证连接
            try (Connection conn = dataSource.getConnection()) {
                logger.info("PostgreSQL 连接池初始化成功: {}:{}/{} (poolSize={})", host, port, dbName, poolSize);
            }

            // 创建表
            createTables();
            // 初始化默认管理员用户
            initDefaultUser();
            // 初始化默认SDK驱动配置
            initDefaultDriver();
            // 初始化默认系统配置
            initDefaultConfig();
            return true;
        } catch (Exception e) {
            logger.error("数据库初始化失败", e);
            return false;
        }
    }

    private String envOrDefault(String envKey, String defaultValue) {
        String val = System.getenv(envKey);
        return (val != null && !val.isEmpty()) ? val : defaultValue;
    }

    /**
     * 获取连接。
     * 为兼容大量已有代码（不关闭连接的调用方），此方法返回一个"粘性"连接：
     * 内部持有一个长连接，close() 调用被忽略，连接在 Database.close() 时统一释放。
     * 新代码请优先使用 try-with-resources + dataSource.getConnection()。
     */
    public Connection getConnection() {
        if (stickyConnection == null || isStickyConnectionClosed()) {
            synchronized (this) {
                if (stickyConnection == null || isStickyConnectionClosed()) {
                    try {
                        stickyConnection = dataSource.getConnection();
                        stickyConnection.setAutoCommit(true);
                    } catch (SQLException e) {
                        logger.error("获取粘性连接失败", e);
                        return null;
                    }
                }
            }
        }
        return stickyConnection;
    }

    private boolean isStickyConnectionClosed() {
        try {
            return stickyConnection == null || stickyConnection.isClosed();
        } catch (SQLException e) {
            return true;
        }
    }

    /**
     * 获取连接池中的新连接（用于需要事务或并发安全的新代码）
     */
    public Connection getPoolConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * 创建数据库表（PostgreSQL 语法）
     */
    private void createTables() throws SQLException {
        try (Connection conn = getPoolConnection(); Statement stmt = conn.createStatement()) {

            // devices 表
            stmt.execute("CREATE TABLE IF NOT EXISTS devices (" +
                    "id SERIAL PRIMARY KEY, " +
                    "device_id TEXT UNIQUE NOT NULL, " +
                    "ip TEXT NOT NULL, " +
                    "port INTEGER NOT NULL, " +
                    "name TEXT, " +
                    "username TEXT, " +
                    "password TEXT, " +
                    "rtsp_url TEXT, " +
                    "status INTEGER DEFAULT 0, " +
                    "user_id INTEGER DEFAULT -1, " +
                    "channel INTEGER DEFAULT 1, " +
                    "brand TEXT DEFAULT 'auto', " +
                    "camera_type TEXT, " +
                    "serial_number TEXT, " +
                    "last_seen TIMESTAMP DEFAULT NOW(), " +
                    "created_at TIMESTAMP DEFAULT NOW(), " +
                    "updated_at TIMESTAMP DEFAULT NOW()" +
                    ")");

            // users 表
            stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "id SERIAL PRIMARY KEY, " +
                    "username TEXT UNIQUE NOT NULL, " +
                    "password TEXT NOT NULL, " +
                    "created_at TIMESTAMP DEFAULT NOW(), " +
                    "updated_at TIMESTAMP DEFAULT NOW()" +
                    ")");

            // configs 表
            stmt.execute("CREATE TABLE IF NOT EXISTS configs (" +
                    "id SERIAL PRIMARY KEY, " +
                    "config_key TEXT UNIQUE NOT NULL, " +
                    "config_value TEXT, " +
                    "config_type TEXT DEFAULT 'string', " +
                    "created_at TIMESTAMP DEFAULT NOW(), " +
                    "updated_at TIMESTAMP DEFAULT NOW()" +
                    ")");

            // drivers 表
            stmt.execute("CREATE TABLE IF NOT EXISTS drivers (" +
                    "id SERIAL PRIMARY KEY, " +
                    "driver_id TEXT UNIQUE NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "version TEXT, " +
                    "lib_path TEXT, " +
                    "log_path TEXT, " +
                    "log_level INTEGER DEFAULT 1, " +
                    "status TEXT DEFAULT 'INACTIVE', " +
                    "created_at TIMESTAMP DEFAULT NOW(), " +
                    "updated_at TIMESTAMP DEFAULT NOW()" +
                    ")");

            // device_history 表
            stmt.execute("CREATE TABLE IF NOT EXISTS device_history (" +
                    "id SERIAL PRIMARY KEY, " +
                    "device_id TEXT NOT NULL, " +
                    "status INTEGER NOT NULL, " +
                    "recorded_at TIMESTAMP DEFAULT NOW()" +
                    ")");

            // alarm_history 表
            stmt.execute("CREATE TABLE IF NOT EXISTS alarm_history (" +
                    "id SERIAL PRIMARY KEY, " +
                    "device_id TEXT, " +
                    "alarm_type TEXT NOT NULL, " +
                    "alarm_level TEXT DEFAULT 'warning', " +
                    "message TEXT, " +
                    "recorded_at TIMESTAMP DEFAULT NOW()" +
                    ")");

            // notifications 表
            stmt.execute("CREATE TABLE IF NOT EXISTS notifications (" +
                    "id SERIAL PRIMARY KEY, " +
                    "title TEXT NOT NULL, " +
                    "message TEXT NOT NULL, " +
                    "type TEXT DEFAULT 'info', " +
                    "read INTEGER DEFAULT 0, " +
                    "created_at TIMESTAMP DEFAULT NOW()" +
                    ")");

            // workflow_execution_history 表
            stmt.execute("CREATE TABLE IF NOT EXISTS workflow_execution_history (" +
                    "id SERIAL PRIMARY KEY, " +
                    "flow_id TEXT NOT NULL, " +
                    "rule_id TEXT, " +
                    "device_id TEXT, " +
                    "executed_at TIMESTAMP DEFAULT NOW()" +
                    ")");

            // 时序监控表：设备在线率（每分钟一条）
            stmt.execute("CREATE TABLE IF NOT EXISTS device_metrics (" +
                    "id BIGSERIAL PRIMARY KEY, " +
                    "time TIMESTAMPTZ NOT NULL DEFAULT NOW(), " +
                    "total_count INTEGER NOT NULL, " +
                    "online_count INTEGER NOT NULL" +
                    ")");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_device_metrics_time ON device_metrics (time DESC)");

            // 时序监控表：雷达点云帧率（按设备、每周期一条）
            stmt.execute("CREATE TABLE IF NOT EXISTS radar_frame_metrics (" +
                    "id BIGSERIAL PRIMARY KEY, " +
                    "time TIMESTAMPTZ NOT NULL DEFAULT NOW(), " +
                    "device_id TEXT NOT NULL, " +
                    "frame_count INTEGER NOT NULL, " +
                    "point_count BIGINT NOT NULL, " +
                    "fps FLOAT8 NOT NULL" +
                    ")");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_radar_frame_metrics_device_time " +
                    "ON radar_frame_metrics (device_id, time DESC)");

            // 索引
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_device_id ON devices(device_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_ip_port ON devices(ip, port)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_brand ON devices(brand)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_config_key ON configs(config_key)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_driver_id ON drivers(driver_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_device_history_device_id ON device_history(device_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_device_history_recorded_at ON device_history(recorded_at)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_alarm_history_recorded_at ON alarm_history(recorded_at)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_notifications_read ON notifications(read)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_notifications_created_at ON notifications(created_at)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_workflow_execution_flow_id ON workflow_execution_history(flow_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_workflow_execution_executed_at ON workflow_execution_history(executed_at)");

            // 创建业务表（各 Table 类）
            AssemblyTable.createTables(conn);
            AlarmRuleTable.createTables(conn);
            AlarmRecordTable.createTables(conn);
            AlarmFlowTable.createTables(conn);
            SpeakerTable.createTables(conn);
            RecordingTaskTable.createTables(conn);
            RadarDeviceTable.createTables(conn);
            RadarBackgroundTable.createTables(conn);
            RadarDefenseZoneTable.createTables(conn);
            CanonicalEventTable.createTables(conn);
            DevicePtzExtensionTable.createTables(conn);
            AiAnalysisRecordTable.createTables(conn);

            logger.info("数据库表创建成功");
        }
    }

    // ==================== 设备管理 ====================

    public boolean saveOrUpdateDevice(DeviceInfo device) {
        String sql = "INSERT INTO devices " +
                "(device_id, ip, port, name, username, password, rtsp_url, status, user_id, channel, brand, camera_type, serial_number, last_seen, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW()) " +
                "ON CONFLICT (device_id) DO UPDATE SET " +
                "ip=EXCLUDED.ip, port=EXCLUDED.port, name=EXCLUDED.name, " +
                "username=EXCLUDED.username, password=EXCLUDED.password, rtsp_url=EXCLUDED.rtsp_url, " +
                "status=EXCLUDED.status, user_id=EXCLUDED.user_id, channel=EXCLUDED.channel, " +
                "brand=EXCLUDED.brand, camera_type=EXCLUDED.camera_type, serial_number=EXCLUDED.serial_number, " +
                "last_seen=NOW(), updated_at=NOW()";

        try (Connection conn = getPoolConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
            pstmt.setString(12, device.getCameraType() != null ? device.getCameraType() : "other");
            pstmt.setString(13, device.getSerialNumber());
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.error("保存设备信息失败", e);
            return false;
        }
    }

    public DeviceInfo getDevice(String deviceId) {
        String sql = "SELECT * FROM devices WHERE device_id = ?";
        try (Connection conn = getPoolConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, deviceId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return mapResultSetToDevice(rs);
        } catch (SQLException e) {
            logger.error("查询设备信息失败: {}", deviceId, e);
        }
        return null;
    }

    public DeviceInfo getDeviceByIpPort(String ip, int port) {
        String sql = "SELECT * FROM devices WHERE ip = ? AND port = ?";
        try (Connection conn = getPoolConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, ip);
            pstmt.setInt(2, port);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return mapResultSetToDevice(rs);
        } catch (SQLException e) {
            logger.error("查询设备信息失败: {}:{}", ip, port, e);
        }
        return null;
    }

    public List<DeviceInfo> getAllDevices() {
        List<DeviceInfo> devices = new ArrayList<>();
        String sql = "SELECT * FROM devices ORDER BY created_at DESC";
        try (Connection conn = getPoolConnection(); Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) devices.add(mapResultSetToDevice(rs));
        } catch (SQLException e) {
            logger.error("查询所有设备失败", e);
        }
        return devices;
    }

    public boolean updateDeviceStatus(String deviceId, int status, int userId) {
        String sql = status == 1
                ? "UPDATE devices SET status=?, user_id=?, last_seen=NOW(), updated_at=NOW() WHERE device_id=?"
                : "UPDATE devices SET status=?, user_id=?, updated_at=NOW() WHERE device_id=?";
        try (Connection conn = getPoolConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, status);
            pstmt.setInt(2, userId);
            pstmt.setString(3, deviceId);
            int rows = pstmt.executeUpdate();
            if (rows > 0) recordDeviceHistory(deviceId, status);
            return rows > 0;
        } catch (SQLException e) {
            logger.error("更新设备状态失败: {}", deviceId, e);
            return false;
        }
    }

    public boolean updateLastSeen(String deviceId) {
        String sql = "UPDATE devices SET last_seen=NOW() WHERE device_id=?";
        try (Connection conn = getPoolConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, deviceId);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.error("更新设备最后发现时间失败: {}", deviceId, e);
            return false;
        }
    }

    public boolean deleteDevice(String deviceId) {
        String sql = "DELETE FROM devices WHERE device_id=?";
        try (Connection conn = getPoolConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, deviceId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("删除设备失败: {}", deviceId, e);
            return false;
        }
    }

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
        device.setBrand(rs.getString("brand"));
        device.setCameraType(rs.getString("camera_type"));
        device.setSerialNumber(rs.getString("serial_number"));
        device.setLastSeen(rs.getTimestamp("last_seen"));
        device.setCreatedAt(rs.getTimestamp("created_at"));
        device.setUpdatedAt(rs.getTimestamp("updated_at"));
        return device;
    }

    // ==================== 用户管理 ====================

    private void initDefaultUser() {
        if (!userExists("admin")) {
            String hash = org.mindrot.jbcrypt.BCrypt.hashpw("123456", org.mindrot.jbcrypt.BCrypt.gensalt());
            if (createUser("admin", hash)) {
                logger.info("默认管理员用户已创建: admin");
            }
        }
    }

    public boolean createUser(String username, String passwordHash) {
        String sql = "INSERT INTO users (username, password) VALUES (?, ?) ON CONFLICT (username) DO NOTHING";
        try (Connection conn = getPoolConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
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

    public String getUserPasswordHash(String username) {
        String sql = "SELECT password FROM users WHERE username=?";
        try (Connection conn = getPoolConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getString("password");
        } catch (SQLException e) {
            logger.error("查询用户密码失败: {}", username, e);
        }
        return null;
    }

    public boolean userExists(String username) {
        String sql = "SELECT COUNT(*) FROM users WHERE username=?";
        try (Connection conn = getPoolConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getInt(1) > 0;
        } catch (SQLException e) {
            logger.error("检查用户是否存在失败: {}", username, e);
        }
        return false;
    }

    public boolean updateUserPassword(String username, String passwordHash) {
        String sql = "UPDATE users SET password=?, updated_at=NOW() WHERE username=?";
        try (Connection conn = getPoolConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, passwordHash);
            pstmt.setString(2, username);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("更新用户密码失败: {}", username, e);
            return false;
        }
    }

    // ==================== 配置管理 ====================

    private void initDefaultConfig() {
        if (getConfig("scanner.enabled") == null) {
            saveOrUpdateConfig("scanner.enabled", "false", "boolean");
        }
        if (getConfig("mqtt.status_topic") == null) {
            saveOrUpdateConfig("mqtt.status_topic", DEFAULT_MQTT_STATUS_TOPIC, "string");
        }
        if (getConfig("mqtt.command_topic") == null) {
            saveOrUpdateConfig("mqtt.command_topic", DEFAULT_MQTT_COMMAND_TOPIC, "string");
        }
    }

    public boolean saveOrUpdateConfig(String key, String value, String type) {
        String sql = "INSERT INTO configs (config_key, config_value, config_type, updated_at) VALUES (?, ?, ?, NOW()) " +
                "ON CONFLICT (config_key) DO UPDATE SET config_value=EXCLUDED.config_value, config_type=EXCLUDED.config_type, updated_at=NOW()";
        try (Connection conn = getPoolConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, key);
            pstmt.setString(2, value);
            pstmt.setString(3, type != null ? type : "string");
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.error("保存配置失败: {}", key, e);
            return false;
        }
    }

    public String getConfig(String key) {
        String sql = "SELECT config_value FROM configs WHERE config_key=?";
        try (Connection conn = getPoolConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, key);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getString("config_value");
        } catch (SQLException e) {
            logger.error("查询配置失败: {}", key, e);
        }
        return null;
    }

    public Map<String, String> getAllConfigs() {
        Map<String, String> configs = new HashMap<>();
        String sql = "SELECT config_key, config_value FROM configs";
        try (Connection conn = getPoolConnection(); Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) configs.put(rs.getString("config_key"), rs.getString("config_value"));
        } catch (SQLException e) {
            logger.error("查询所有配置失败", e);
        }
        return configs;
    }

    public boolean deleteConfig(String key) {
        String sql = "DELETE FROM configs WHERE config_key=?";
        try (Connection conn = getPoolConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, key);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("删除配置失败: {}", key, e);
            return false;
        }
    }

    // ==================== 驱动管理 ====================

    private void initDefaultDriver() {
        String userDir = System.getProperty("user.dir");
        String archDir = LibraryPathHelper.getArchitectureDir();
        String osName = System.getProperty("os.name").toLowerCase();
        String osType = (osName.contains("mac") || osName.contains("darwin")) ? "macos" : "linux";

        String hikvisionPath = detectActualSDKLibPath("hikvision");
        if (hikvisionPath == null) {
            String defaultPath = LibraryPathHelper.getSDKLibPath("hikvision");
            hikvisionPath = defaultPath != null ? defaultPath.replace(userDir + "/", "./") : "./lib/" + archDir + "/hikvision";
        }
        initDefaultDriverWithConfig("hikvision_sdk", "Hikvision SDK", "6.1.9.45", hikvisionPath, "./sdkLog", 3, "ACTIVE");

        if ("x86".equals(archDir)) {
            String tiandyPath = detectActualSDKLibPath("tiandy");
            if (tiandyPath == null) {
                String defaultPath = LibraryPathHelper.getSDKLibPath("tiandy");
                tiandyPath = defaultPath != null ? defaultPath.replace(userDir + "/", "./") : "./lib/x86/tiandy";
            }
            initDefaultDriverWithConfig("tiandy_sdk", "Tiandy SDK", "1.0.0", tiandyPath, "./sdkLog", 3, "ACTIVE");
        }

        String dahuaPath = detectActualSDKLibPath("dahua");
        if (dahuaPath == null) {
            String defaultPath = LibraryPathHelper.getSDKLibPath("dahua");
            dahuaPath = defaultPath != null ? defaultPath.replace(userDir + "/", "./") : "./lib/" + archDir + "/dahua";
        }
        initDefaultDriverWithConfig("dahua_sdk", "Dahua SDK", "1.0.0", dahuaPath, "./sdkLog", 3, "ACTIVE");

        String livoxPath = detectActualSDKLibPath("livox");
        if (livoxPath == null) {
            String defaultPath = LibraryPathHelper.getLivoxLibPath(osType);
            livoxPath = defaultPath.replace(userDir + "/", "./");
        }
        initDefaultDriverWithConfig("livox_sdk", "Livox Radar SDK", "1.0.0", livoxPath, "./sdkLog", 3, "ACTIVE");
    }

    private String detectActualSDKLibPath(String sdkName) {
        String userDir = System.getProperty("user.dir");
        String archDir = LibraryPathHelper.getArchitectureDir();
        String osName = System.getProperty("os.name").toLowerCase();
        String osType = (osName.contains("mac") || osName.contains("darwin")) ? "macos" : "linux";

        String[] libraryFileNames;
        String[] searchPaths;

        if ("livox".equalsIgnoreCase(sdkName)) {
            String libExt = osType.contains("mac") ? ".dylib" : ".so";
            libraryFileNames = new String[]{"liblivoxjni" + libExt};
            searchPaths = new String[]{userDir + "/lib/" + osType, userDir + "/lib/linux", userDir + "/lib/macos"};
        } else {
            if ("hikvision".equalsIgnoreCase(sdkName)) {
                libraryFileNames = new String[]{"libhcnetsdk.so", "libhcnetsdk.so.x86_64", "libhcnetsdk.so.aarch64"};
            } else if ("tiandy".equalsIgnoreCase(sdkName)) {
                libraryFileNames = new String[]{"libnvssdk.so", "libnvssdk.so.x86_64"};
            } else if ("dahua".equalsIgnoreCase(sdkName)) {
                libraryFileNames = new String[]{"libdhnetsdk.so", "libdhnetsdk.so.x86_64", "libdhnetsdk.so.aarch64"};
            } else {
                return null;
            }
            searchPaths = new String[]{
                userDir + "/lib/" + archDir + "/" + sdkName,
                userDir + "/lib/x86/" + sdkName,
                userDir + "/lib/arm/" + sdkName,
                userDir + "/lib/" + sdkName
            };
        }

        for (String searchPath : searchPaths) {
            File searchDir = new File(searchPath);
            if (searchDir.exists() && searchDir.isDirectory()) {
                for (String libFileName : libraryFileNames) {
                    if (new File(searchDir, libFileName).exists()) {
                        return searchPath.replace(userDir + "/", "./");
                    }
                }
                File[] files = searchDir.listFiles((dir, name) -> name.endsWith(".so") || name.endsWith(".dylib"));
                if (files != null && files.length > 0) {
                    return searchPath.replace(userDir + "/", "./");
                }
            }
        }
        return null;
    }

    public void initDefaultDriverWithConfig(String driverId, String name, String version,
            String libPath, String logPath, int logLevel, String status) {
        if (getDriver(driverId) == null) {
            saveOrUpdateDriver(driverId, name, version, libPath, logPath, logLevel, status);
            logger.info("默认SDK驱动配置已创建: {} - {}", driverId, name);
        }
    }

    public boolean saveOrUpdateDriver(String driverId, String name, String version, String libPath, String logPath,
            int logLevel, String status) {
        String sql = "INSERT INTO drivers (driver_id, name, version, lib_path, log_path, log_level, status, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, NOW()) " +
                "ON CONFLICT (driver_id) DO UPDATE SET " +
                "name=EXCLUDED.name, version=EXCLUDED.version, lib_path=EXCLUDED.lib_path, " +
                "log_path=EXCLUDED.log_path, log_level=EXCLUDED.log_level, status=EXCLUDED.status, updated_at=NOW()";
        try (Connection conn = getPoolConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, driverId);
            pstmt.setString(2, name);
            pstmt.setString(3, version);
            pstmt.setString(4, libPath);
            pstmt.setString(5, logPath);
            pstmt.setInt(6, logLevel);
            pstmt.setString(7, status != null ? status : "INACTIVE");
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.error("保存驱动配置失败: {}", driverId, e);
            return false;
        }
    }

    public Map<String, Object> getDriver(String driverId) {
        String sql = "SELECT * FROM drivers WHERE driver_id=?";
        try (Connection conn = getPoolConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, driverId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                Map<String, Object> driver = new HashMap<>();
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

    public List<Map<String, Object>> getAllDrivers() {
        List<Map<String, Object>> drivers = new ArrayList<>();
        String sql = "SELECT * FROM drivers ORDER BY created_at DESC";
        try (Connection conn = getPoolConnection(); Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> driver = new HashMap<>();
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

    public boolean deleteDriver(String driverId) {
        String sql = "DELETE FROM drivers WHERE driver_id=?";
        try (Connection conn = getPoolConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, driverId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("删除驱动配置失败: {}", driverId, e);
            return false;
        }
    }

    // ==================== 历史数据 ====================

    public boolean recordDeviceHistory(String deviceId, int status) {
        String sql = "INSERT INTO device_history (device_id, status, recorded_at) VALUES (?, ?, NOW())";
        try (Connection conn = getPoolConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, deviceId);
            pstmt.setInt(2, status);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.error("记录设备状态历史失败: {}", deviceId, e);
            return false;
        }
    }

    public boolean recordAlarm(String deviceId, String alarmType, String alarmLevel, String message) {
        String sql = "INSERT INTO alarm_history (device_id, alarm_type, alarm_level, message, recorded_at) VALUES (?, ?, ?, ?, NOW())";
        try (Connection conn = getPoolConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
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

    public boolean recordWorkflowExecution(String flowId, String ruleId, String deviceId) {
        String sql = "INSERT INTO workflow_execution_history (flow_id, rule_id, device_id, executed_at) VALUES (?, ?, ?, NOW())";
        try (Connection conn = getPoolConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, flowId);
            pstmt.setString(2, ruleId);
            pstmt.setString(3, deviceId);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.error("记录工作流执行失败: flowId={}", flowId, e);
            return false;
        }
    }

    // ==================== 时序监控数据写入 ====================

    /**
     * 写入设备在线率时序数据（每分钟由 DeviceMetricsCollector 调用）
     */
    public boolean insertDeviceMetrics(int totalCount, int onlineCount) {
        String sql = "INSERT INTO device_metrics (time, total_count, online_count) VALUES (NOW(), ?, ?)";
        try (Connection conn = getPoolConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, totalCount);
            pstmt.setInt(2, onlineCount);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.error("写入设备在线率时序数据失败", e);
            return false;
        }
    }

    /**
     * 写入雷达点云帧率时序数据（每个统计周期由 RadarService 调用）
     */
    public boolean insertRadarFrameMetric(String deviceId, long frameCount, long pointCount, double fps) {
        String sql = "INSERT INTO radar_frame_metrics (time, device_id, frame_count, point_count, fps) VALUES (NOW(), ?, ?, ?, ?)";
        try (Connection conn = getPoolConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, deviceId);
            pstmt.setLong(2, frameCount);
            pstmt.setLong(3, pointCount);
            pstmt.setDouble(4, fps);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.error("写入雷达帧率时序数据失败: deviceId={}", deviceId, e);
            return false;
        }
    }

    // ==================== 趋势图查询（PostgreSQL 时区函数）====================

    public List<Map<String, Object>> getDeviceConnectivityTrend24h() {
        List<Map<String, Object>> trendData = new ArrayList<>();
        try {
            int currentOnlineCount = 0;
            String currentOnlineSql = "SELECT COUNT(*) as cnt FROM devices WHERE status=1";
            try (Connection conn = getPoolConnection(); Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(currentOnlineSql)) {
                if (rs.next()) currentOnlineCount = rs.getInt("cnt");
            }

            String sql = "SELECT TO_CHAR(recorded_at AT TIME ZONE 'Asia/Shanghai', 'HH24') as hour, " +
                    "COUNT(DISTINCT CASE WHEN CAST(status AS INTEGER) = 1 THEN device_id END) as online_count " +
                    "FROM device_history " +
                    "WHERE recorded_at >= NOW() - INTERVAL '24 hours' " +
                    "GROUP BY TO_CHAR(recorded_at AT TIME ZONE 'Asia/Shanghai', 'HH24') " +
                    "ORDER BY hour";

            Map<String, Integer> hourData = new HashMap<>();
            try (Connection conn = getPoolConnection(); Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) hourData.put(rs.getString("hour"), rs.getInt("online_count"));
            }

            int[] timePoints = {0, 4, 8, 12, 16, 20};
            for (int h : timePoints) {
                Map<String, Object> dp = new HashMap<>();
                String hs = String.format("%02d", h);
                dp.put("name", hs + ":00");
                dp.put("online", hourData.getOrDefault(hs, currentOnlineCount));
                trendData.add(dp);
            }
        } catch (SQLException e) {
            logger.error("获取设备连接趋势失败", e);
        }
        return trendData;
    }

    public int getAlarmCount24h() {
        String sql = "SELECT COUNT(*) as cnt FROM alarm_records WHERE recorded_at >= NOW() - INTERVAL '24 hours'";
        try (Connection conn = getPoolConnection(); Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) return rs.getInt("cnt");
        } catch (SQLException e) {
            logger.error("获取24小时告警数量失败", e);
        }
        return 0;
    }

    public List<Map<String, Object>> getAlarmEventTrend24h() {
        List<Map<String, Object>> trendData = new ArrayList<>();
        try {
            String sql = "SELECT TO_CHAR(recorded_at AT TIME ZONE 'Asia/Shanghai', 'HH24') as hour, " +
                    "COUNT(*) as alarm_count " +
                    "FROM alarm_records " +
                    "WHERE recorded_at >= NOW() - INTERVAL '24 hours' " +
                    "GROUP BY TO_CHAR(recorded_at AT TIME ZONE 'Asia/Shanghai', 'HH24') " +
                    "ORDER BY hour";

            Map<String, Integer> hourData = new HashMap<>();
            try (Connection conn = getPoolConnection(); Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) hourData.put(rs.getString("hour"), rs.getInt("alarm_count"));
            }

            for (int h = 0; h < 24; h++) {
                Map<String, Object> dp = new HashMap<>();
                String hs = String.format("%02d", h);
                dp.put("name", hs + ":00");
                dp.put("alarms", hourData.getOrDefault(hs, 0));
                trendData.add(dp);
            }
        } catch (SQLException e) {
            logger.error("获取报警事件趋势失败", e);
            for (int h = 0; h < 24; h++) {
                Map<String, Object> dp = new HashMap<>();
                dp.put("name", String.format("%02d:00", h));
                dp.put("alarms", 0);
                trendData.add(dp);
            }
        }
        return trendData;
    }

    public List<Map<String, Object>> getWorkflowExecutionTrend24h() {
        List<Map<String, Object>> trendData = new ArrayList<>();
        try {
            String sql = "SELECT TO_CHAR(executed_at AT TIME ZONE 'Asia/Shanghai', 'HH24') as hour, " +
                    "COUNT(*) as workflow_count " +
                    "FROM workflow_execution_history " +
                    "WHERE executed_at >= NOW() - INTERVAL '24 hours' " +
                    "GROUP BY TO_CHAR(executed_at AT TIME ZONE 'Asia/Shanghai', 'HH24') " +
                    "ORDER BY hour";

            Map<String, Integer> hourData = new HashMap<>();
            try (Connection conn = getPoolConnection(); Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) hourData.put(rs.getString("hour"), rs.getInt("workflow_count"));
            }

            for (int h = 0; h < 24; h++) {
                Map<String, Object> dp = new HashMap<>();
                String hs = String.format("%02d", h);
                dp.put("name", hs + ":00");
                dp.put("workflows", hourData.getOrDefault(hs, 0));
                trendData.add(dp);
            }
        } catch (SQLException e) {
            logger.error("获取工作流执行趋势失败", e);
            for (int h = 0; h < 24; h++) {
                Map<String, Object> dp = new HashMap<>();
                dp.put("name", String.format("%02d:00", h));
                dp.put("workflows", 0);
                trendData.add(dp);
            }
        }
        return trendData;
    }

    /**
     * 清理旧的历史数据（保留30天）
     */
    public void cleanupOldHistory() {
        try (Connection conn = getPoolConnection(); Statement stmt = conn.createStatement()) {
            int d1 = stmt.executeUpdate("DELETE FROM device_history WHERE recorded_at < NOW() - INTERVAL '30 days'");
            int d2 = stmt.executeUpdate("DELETE FROM alarm_history WHERE recorded_at < NOW() - INTERVAL '30 days'");
            int d3 = stmt.executeUpdate("DELETE FROM notifications WHERE created_at < NOW() - INTERVAL '30 days'");
            int d4 = stmt.executeUpdate("DELETE FROM device_metrics WHERE time < NOW() - INTERVAL '7 days'");
            int d5 = stmt.executeUpdate("DELETE FROM radar_frame_metrics WHERE time < NOW() - INTERVAL '7 days'");
            logger.info("清理旧历史数据: device_history={}, alarm_history={}, notifications={}, device_metrics={}, radar_frame_metrics={}",
                    d1, d2, d3, d4, d5);
        } catch (SQLException e) {
            logger.error("清理旧历史数据失败", e);
        }
    }

    // ==================== 通知管理 ====================

    public boolean createNotification(String title, String message, String type) {
        String sql = "INSERT INTO notifications (title, message, type, read, created_at) VALUES (?, ?, ?, 0, NOW())";
        try (Connection conn = getPoolConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, title);
            pstmt.setString(2, message);
            pstmt.setString(3, type != null ? type : "info");
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.error("创建通知失败", e);
            return false;
        }
    }

    public List<Map<String, Object>> getAllNotifications(int limit) {
        List<Map<String, Object>> notifications = new ArrayList<>();
        String sql = "SELECT * FROM notifications ORDER BY created_at DESC LIMIT ?";
        try (Connection conn = getPoolConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, limit > 0 ? limit : 100);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> n = new HashMap<>();
                n.put("id", String.valueOf(rs.getInt("id")));
                n.put("title", rs.getString("title"));
                n.put("message", rs.getString("message"));
                n.put("type", rs.getString("type"));
                n.put("read", rs.getInt("read") == 1);
                n.put("time", formatTimeAgo(rs.getTimestamp("created_at")));
                notifications.add(n);
            }
        } catch (SQLException e) {
            logger.error("获取通知列表失败", e);
        }
        return notifications;
    }

    public boolean markNotificationAsRead(String notificationId) {
        String sql = "UPDATE notifications SET read=1 WHERE id=?";
        try (Connection conn = getPoolConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(notificationId));
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("标记通知为已读失败: {}", notificationId, e);
            return false;
        }
    }

    public boolean markAllNotificationsAsRead() {
        String sql = "UPDATE notifications SET read=1 WHERE read=0";
        try (Connection conn = getPoolConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            return true;
        } catch (SQLException e) {
            logger.error("标记所有通知为已读失败", e);
            return false;
        }
    }

    private String formatTimeAgo(java.sql.Timestamp timestamp) {
        if (timestamp == null) return "未知";
        long diff = System.currentTimeMillis() - timestamp.getTime();
        if (diff < 60000) return "刚刚";
        if (diff < 3600000) return (diff / 60000) + "分钟前";
        if (diff < 86400000) return (diff / 3600000) + "小时前";
        return (diff / 86400000) + "天前";
    }

    // ==================== 国标 ID 管理 ====================

    public boolean setDeviceGbId(String currentDeviceId, String newGbId) {
        if (currentDeviceId == null || newGbId == null || !Gb28181IdGenerator.isGb28181Format(newGbId)) return false;
        if (currentDeviceId.equals(newGbId)) return true;
        if (getDevice(newGbId) != null) {
            logger.warn("国标 ID 已存在，无法设置: {}", newGbId);
            return false;
        }
        try (Connection conn = getPoolConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement pstmt = conn.prepareStatement(
                        "UPDATE devices SET device_id=?, updated_at=NOW() WHERE device_id=?")) {
                    pstmt.setString(1, newGbId);
                    pstmt.setString(2, currentDeviceId);
                    if (pstmt.executeUpdate() == 0) { conn.rollback(); return false; }
                }
                String[] tables = {"device_history", "alarm_history", "workflow_execution_history",
                        "assembly_devices", "alarm_rules", "device_ptz_extension", "device_event_subscriptions",
                        "radar_devices", "radar_backgrounds", "radar_intrusion_records",
                        "recording_tasks", "speakers", "alarm_records", "radar_defense_zones"};
                for (String table : tables) updateTableDeviceId(conn, table, "device_id", currentDeviceId, newGbId);
                updateTableDeviceId(conn, "radar_defense_zones", "camera_device_id", currentDeviceId, newGbId);
                conn.commit();
                logger.info("设备国标 ID 已更新: {} -> {}", currentDeviceId, newGbId);
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            logger.error("设置设备国标 ID 失败: {} -> {}", currentDeviceId, newGbId, e);
            return false;
        }
    }

    private void updateTableDeviceId(Connection conn, String table, String column, String oldId, String newId) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(
                "UPDATE " + table + " SET " + column + "=? WHERE " + column + "=?")) {
            pstmt.setString(1, newId);
            pstmt.setString(2, oldId);
            pstmt.executeUpdate();
        }
    }

    public String suggestDeviceGbId() {
        String seqStr = getConfig("gb28181_device_sequence");
        long sequence = 0;
        if (seqStr != null && !seqStr.isEmpty()) {
            try { sequence = Long.parseLong(seqStr.trim()); } catch (NumberFormatException ignored) {}
        }
        String newId = new Gb28181IdGenerator().generate(sequence);
        saveOrUpdateConfig("gb28181_device_sequence", String.valueOf(sequence + 1), "string");
        return newId;
    }

    // ==================== PTZ 扩展 ====================

    public DevicePtzExtensionTable.PtzExtension getPtzExtension(String deviceId) {
        try (Connection conn = getPoolConnection()) {
            return DevicePtzExtensionTable.getByDeviceId(conn, deviceId);
        } catch (SQLException e) {
            logger.error("获取PTZ扩展失败: {}", deviceId, e);
            return null;
        }
    }

    public boolean savePtzExtension(DevicePtzExtensionTable.PtzExtension ext) {
        try (Connection conn = getPoolConnection()) {
            return DevicePtzExtensionTable.saveOrUpdate(conn, ext);
        } catch (SQLException e) {
            logger.error("保存PTZ扩展失败", e);
            return false;
        }
    }

    public java.util.List<DevicePtzExtensionTable.PtzExtension> getAllEnabledPtzDevices() {
        try (Connection conn = getPoolConnection()) {
            return DevicePtzExtensionTable.getAllEnabled(conn);
        } catch (SQLException e) {
            logger.error("获取所有启用PTZ设备失败", e);
            return new ArrayList<>();
        }
    }

    public java.util.List<DevicePtzExtensionTable.PtzExtension> getAllPtzExtensions() {
        try (Connection conn = getPoolConnection()) {
            return DevicePtzExtensionTable.getAll(conn);
        } catch (SQLException e) {
            logger.error("获取所有PTZ扩展失败", e);
            return new ArrayList<>();
        }
    }

    public boolean updatePtzPosition(String deviceId, float pan, float tilt, float zoom,
            float azimuth, float horizontalFov, float verticalFov, float visibleRadius) {
        try (Connection conn = getPoolConnection()) {
            return DevicePtzExtensionTable.updatePtzPosition(conn, deviceId,
                    pan, tilt, zoom, azimuth, horizontalFov, verticalFov, visibleRadius);
        } catch (SQLException e) {
            logger.error("更新PTZ位置失败: {}", deviceId, e);
            return false;
        }
    }

    public boolean setPtzMonitorEnabled(String deviceId, boolean enabled) {
        try (Connection conn = getPoolConnection()) {
            return DevicePtzExtensionTable.setPtzEnabled(conn, deviceId, enabled);
        } catch (SQLException e) {
            logger.error("设置PTZ监控开关失败: {}", deviceId, e);
            return false;
        }
    }

    public boolean deletePtzExtension(String deviceId) {
        try (Connection conn = getPoolConnection()) {
            return DevicePtzExtensionTable.delete(conn, deviceId);
        } catch (SQLException e) {
            logger.error("删除PTZ扩展失败: {}", deviceId, e);
            return false;
        }
    }

    // ==================== 关闭 ====================

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("数据库连接池已关闭");
        }
    }
}
