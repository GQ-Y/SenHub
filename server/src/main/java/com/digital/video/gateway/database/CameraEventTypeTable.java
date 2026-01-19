package com.digital.video.gateway.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 摄像头事件类型表管理类
 * 管理不同品牌摄像头的报警事件类型定义
 */
public class CameraEventTypeTable {
    private static final Logger logger = LoggerFactory.getLogger(CameraEventTypeTable.class);

    /**
     * 创建摄像头事件类型相关表
     */
    public static void createTables(Connection connection) throws SQLException {
        // camera_event_types 表 - 事件类型定义
        String createEventTypesTable = "CREATE TABLE IF NOT EXISTS camera_event_types (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "brand TEXT NOT NULL, " + // 摄像头品牌: tiandy, hikvision
                "event_code INTEGER NOT NULL, " + // 事件代码
                "event_name TEXT NOT NULL, " + // 事件名称
                "event_name_en TEXT, " + // 英文名称
                "category TEXT DEFAULT 'vca', " + // 分类: basic, vca, face, its
                "description TEXT, " + // 描述
                "enabled INTEGER DEFAULT 1, " + // 是否可用
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE(brand, event_code)" +
                ")";

        // device_event_subscriptions 表 - 设备事件订阅
        String createSubscriptionsTable = "CREATE TABLE IF NOT EXISTS device_event_subscriptions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "device_id TEXT NOT NULL, " + // 设备ID
                "event_type_id INTEGER NOT NULL, " + // 事件类型ID
                "enabled INTEGER DEFAULT 1, " + // 是否启用
                "mqtt_forward INTEGER DEFAULT 1, " + // 是否MQTT转发
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (device_id) REFERENCES devices(device_id), " +
                "FOREIGN KEY (event_type_id) REFERENCES camera_event_types(id), " +
                "UNIQUE(device_id, event_type_id)" +
                ")";

        // 创建索引
        String createIndex = "CREATE INDEX IF NOT EXISTS idx_event_types_brand ON camera_event_types(brand); " +
                "CREATE INDEX IF NOT EXISTS idx_event_types_category ON camera_event_types(category); " +
                "CREATE INDEX IF NOT EXISTS idx_subscriptions_device_id ON device_event_subscriptions(device_id); " +
                "CREATE INDEX IF NOT EXISTS idx_subscriptions_enabled ON device_event_subscriptions(enabled);";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createEventTypesTable);
            stmt.execute(createSubscriptionsTable);
            stmt.execute(createIndex);
            logger.info("摄像头事件类型表创建成功");

            // 初始化默认事件类型
            initDefaultEventTypes(connection);
        }
    }

    /**
     * 初始化默认事件类型
     */
    private static void initDefaultEventTypes(Connection connection) throws SQLException {
        // 检查是否已有数据
        String checkSql = "SELECT COUNT(*) FROM camera_event_types";
        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery(checkSql)) {
            if (rs.next() && rs.getInt(1) > 0) {
                logger.debug("事件类型数据已存在，跳过初始化");
                return;
            }
        }

        // 插入天地伟业事件类型
        initTiandyEventTypes(connection);
        // 插入海康事件类型
        initHikvisionEventTypes(connection);
        // 插入大华事件类型
        initDahuaEventTypes(connection);

        logger.info("默认事件类型初始化完成");
    }

    /**
     * 初始化天地伟业事件类型
     */
    private static void initTiandyEventTypes(Connection connection) throws SQLException {
        String sql = "INSERT INTO camera_event_types (brand, event_code, event_name, event_name_en, category, description) VALUES (?, ?, ?, ?, ?, ?)";

        Object[][] tiandyEvents = {
                // 基础视频报警 (iAlarmType)
                { "tiandy", 0, "移动侦测", "Motion Detection", "basic", "视频画面移动侦测报警" },
                { "tiandy", 1, "录像报警", "Recording Alarm", "basic", "录像状态异常报警" },
                { "tiandy", 2, "视频丢失", "Video Lost", "basic", "视频信号丢失报警" },
                { "tiandy", 3, "开关量输入", "Alarm Input", "basic", "外部开关量输入触发" },
                { "tiandy", 4, "开关量输出", "Alarm Output", "basic", "外部开关量输出触发" },
                { "tiandy", 5, "视频遮挡", "Video Tamper", "basic", "视频遮挡/遮盖报警" },

                // 智能分析事件 (iEventType)
                { "tiandy", 100, "单绊线越界", "Line Crossing", "vca", "单绊线越界检测" },
                { "tiandy", 101, "双绊线越界", "Double Line Crossing", "vca", "双绊线越界检测" },
                { "tiandy", 102, "周界入侵", "Perimeter Intrusion", "vca", "周界/区域入侵检测" },
                { "tiandy", 103, "徘徊检测", "Loitering", "vca", "人员徘徊检测" },
                { "tiandy", 104, "停车检测", "Parking Detection", "vca", "违规停车检测" },
                { "tiandy", 105, "快速奔跑", "Running Detection", "vca", "人员快速奔跑检测" },
                { "tiandy", 106, "区域人员密度", "Crowd Density", "vca", "区域人员密度统计" },
                { "tiandy", 107, "物品遗失", "Object Removal", "vca", "物品被拿走/遗失检测" },
                { "tiandy", 108, "物品遗弃", "Object Left", "vca", "物品遗留检测" },
                { "tiandy", 109, "人脸识别", "Face Recognition", "face", "人脸检测与识别" },
                { "tiandy", 110, "视频诊断", "Video Diagnosis", "vca", "画面异常诊断" },
                { "tiandy", 111, "智能跟踪", "Intelligent Tracking", "vca", "目标自动跟踪" },
                { "tiandy", 112, "交通统计", "Traffic Statistics", "its", "车流/人流量统计" },
                { "tiandy", 113, "人群聚集", "Crowd Gathering", "vca", "人群异常聚集检测" },
                { "tiandy", 114, "离岗检测", "Absence Detection", "vca", "岗位无人/离岗检测" },
                { "tiandy", 115, "音频诊断", "Audio Diagnosis", "vca", "声音异常检测" },
                { "tiandy", 137, "值守检测", "Duty Detection", "vca", "值守状态检测" },
                { "tiandy", 151, "区域滞留", "Region Loitering", "vca", "区域异常滞留检测" },
                { "tiandy", 154, "厨师规范", "Kitchen Safety", "vca", "厨师帽/口罩/工作服检测" },
        };

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            for (Object[] event : tiandyEvents) {
                pstmt.setString(1, (String) event[0]);
                pstmt.setInt(2, (Integer) event[1]);
                pstmt.setString(3, (String) event[2]);
                pstmt.setString(4, (String) event[3]);
                pstmt.setString(5, (String) event[4]);
                pstmt.setString(6, (String) event[5]);
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            logger.info("天地伟业事件类型初始化完成: {} 条", tiandyEvents.length);
        }
    }

    /**
     * 初始化海康事件类型
     */
    private static void initHikvisionEventTypes(Connection connection) throws SQLException {
        String sql = "INSERT INTO camera_event_types (brand, event_code, event_name, event_name_en, category, description) VALUES (?, ?, ?, ?, ?, ?)";

        Object[][] hikvisionEvents = {
                // 基础视频报警
                { "hikvision", 0, "移动侦测", "Motion Detection", "basic", "视频画面移动侦测报警" },
                { "hikvision", 1, "视频丢失", "Video Lost", "basic", "视频信号丢失报警" },
                { "hikvision", 2, "视频遮挡", "Video Tamper", "basic", "视频遮挡报警" },
                { "hikvision", 3, "报警输入", "Alarm Input", "basic", "外部报警输入" },

                // 智能分析事件
                { "hikvision", 100, "越界侦测", "Line Crossing", "vca", "越界检测" },
                { "hikvision", 101, "区域入侵", "Region Intrusion", "vca", "区域入侵检测" },
                { "hikvision", 102, "进入区域", "Region Entrance", "vca", "进入区域检测" },
                { "hikvision", 103, "离开区域", "Region Exiting", "vca", "离开区域检测" },
                { "hikvision", 104, "人员聚集", "People Gathering", "vca", "人员聚集检测" },
                { "hikvision", 105, "快速移动", "Fast Moving", "vca", "快速移动检测" },
                { "hikvision", 106, "徘徊检测", "Loitering", "vca", "人员徘徊检测" },
                { "hikvision", 107, "停车检测", "Parking Detection", "vca", "违规停车检测" },
                { "hikvision", 108, "物品遗留", "Object Left", "vca", "物品遗留检测" },
                { "hikvision", 109, "物品拿取", "Object Removal", "vca", "物品拿取检测" },

                // 人脸事件
                { "hikvision", 200, "人脸抓拍", "Face Capture", "face", "人脸抓拍" },
                { "hikvision", 201, "人脸比对", "Face Match", "face", "人脸比对识别" },

                // 交通事件
                { "hikvision", 300, "车牌识别", "License Plate Recognition", "its", "车牌识别" },
        };

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            for (Object[] event : hikvisionEvents) {
                pstmt.setString(1, (String) event[0]);
                pstmt.setInt(2, (Integer) event[1]);
                pstmt.setString(3, (String) event[2]);
                pstmt.setString(4, (String) event[3]);
                pstmt.setString(5, (String) event[4]);
                pstmt.setString(6, (String) event[5]);
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            logger.info("海康事件类型初始化完成: {} 条", hikvisionEvents.length);
        }
    }

    /**
     * 初始化大华事件类型
     */
    private static void initDahuaEventTypes(Connection connection) throws SQLException {
        String sql = "INSERT INTO camera_event_types (brand, event_code, event_name, event_name_en, category, description) VALUES (?, ?, ?, ?, ?, ?)";

        Object[][] dahuaEvents = {
                // 基础视频报警
                { "dahua", 0, "移动侦测", "Motion Detection", "basic", "视频画面移动侦测报警" },
                { "dahua", 1, "视频丢失", "Video Lost", "basic", "视频信号丢失报警" },
                { "dahua", 2, "视频遮挡", "Video Tamper", "basic", "视频遮挡报警" },
                { "dahua", 3, "报警输入", "Alarm Input", "basic", "外部报警输入触发" },
                { "dahua", 4, "报警输出", "Alarm Output", "basic", "外部报警输出触发" },

                // 智能分析事件
                { "dahua", 100, "越界检测", "Line Crossing", "vca", "越界检测" },
                { "dahua", 101, "区域入侵", "Region Intrusion", "vca", "区域入侵检测" },
                { "dahua", 102, "进入区域", "Region Entrance", "vca", "进入区域检测" },
                { "dahua", 103, "离开区域", "Region Exiting", "vca", "离开区域检测" },
                { "dahua", 104, "人员聚集", "People Gathering", "vca", "人员聚集检测" },
                { "dahua", 105, "徘徊检测", "Loitering", "vca", "人员徘徊检测" },
                { "dahua", 106, "快速移动", "Fast Moving", "vca", "快速移动检测" },
                { "dahua", 107, "停车检测", "Parking Detection", "vca", "违规停车检测" },
                { "dahua", 108, "物品遗留", "Object Left", "vca", "物品遗留检测" },
                { "dahua", 109, "物品拿取", "Object Removal", "vca", "物品拿取检测" },
                { "dahua", 110, "倒地检测", "Tumble Detection", "vca", "人员倒地检测" },
                { "dahua", 111, "打架检测", "Fight Detection", "vca", "打架行为检测" },
                { "dahua", 112, "攀高检测", "Climbing Detection", "vca", "攀爬检测" },

                // 人脸事件
                { "dahua", 200, "人脸检测", "Face Detection", "face", "人脸检测" },
                { "dahua", 201, "人脸识别", "Face Recognition", "face", "人脸识别比对" },
                { "dahua", 202, "人脸抓拍", "Face Capture", "face", "人脸抓拍" },

                // 交通/车辆事件
                { "dahua", 300, "车牌识别", "License Plate Recognition", "its", "车牌识别" },
                { "dahua", 301, "车辆检测", "Vehicle Detection", "its", "车辆检测" },
                { "dahua", 302, "违章停车", "Illegal Parking", "its", "违章停车检测" },
                { "dahua", 303, "逆行检测", "Wrong Way", "its", "车辆逆行检测" },

                // 工服检测
                { "dahua", 400, "安全帽检测", "Helmet Detection", "vca", "安全帽佩戴检测" },
                { "dahua", 401, "反光衣检测", "Vest Detection", "vca", "反光衣穿戴检测" },
                { "dahua", 402, "工服检测", "Work Clothes Detection", "vca", "工作服穿戴检测" },
        };

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            for (Object[] event : dahuaEvents) {
                pstmt.setString(1, (String) event[0]);
                pstmt.setInt(2, (Integer) event[1]);
                pstmt.setString(3, (String) event[2]);
                pstmt.setString(4, (String) event[3]);
                pstmt.setString(5, (String) event[4]);
                pstmt.setString(6, (String) event[5]);
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            logger.info("大华事件类型初始化完成: {} 条", dahuaEvents.length);
        }
    }

    // ==================== 查询方法 ====================

    /**
     * 根据品牌获取事件类型列表
     */
    public static List<Map<String, Object>> getEventTypesByBrand(Connection connection, String brand) {
        List<Map<String, Object>> eventTypes = new ArrayList<>();
        String sql = "SELECT * FROM camera_event_types WHERE brand = ? AND enabled = 1 ORDER BY category, event_code";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, brand);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                eventTypes.add(mapResultSetToEventType(rs));
            }
        } catch (SQLException e) {
            logger.error("查询事件类型失败: brand={}", brand, e);
        }
        return eventTypes;
    }

    /**
     * 获取所有事件类型
     */
    public static List<Map<String, Object>> getAllEventTypes(Connection connection) {
        List<Map<String, Object>> eventTypes = new ArrayList<>();
        String sql = "SELECT * FROM camera_event_types WHERE enabled = 1 ORDER BY brand, category, event_code";

        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                eventTypes.add(mapResultSetToEventType(rs));
            }
        } catch (SQLException e) {
            logger.error("查询所有事件类型失败", e);
        }
        return eventTypes;
    }

    /**
     * 根据ID获取事件类型
     */
    public static Map<String, Object> getEventTypeById(Connection connection, int id) {
        String sql = "SELECT * FROM camera_event_types WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return mapResultSetToEventType(rs);
            }
        } catch (SQLException e) {
            logger.error("查询事件类型失败: id={}", id, e);
        }
        return null;
    }

    // ==================== 设备订阅管理 ====================

    /**
     * 订阅设备事件类型
     */
    public static boolean subscribeDeviceEvent(Connection connection, String deviceId, int eventTypeId,
            boolean mqttForward) {
        String sql = "INSERT OR REPLACE INTO device_event_subscriptions (device_id, event_type_id, enabled, mqtt_forward, updated_at) "
                +
                "VALUES (?, ?, 1, ?, CURRENT_TIMESTAMP)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, deviceId);
            pstmt.setInt(2, eventTypeId);
            pstmt.setInt(3, mqttForward ? 1 : 0);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.error("订阅设备事件失败: deviceId={}, eventTypeId={}", deviceId, eventTypeId, e);
            return false;
        }
    }

    /**
     * 取消订阅设备事件类型
     */
    public static boolean unsubscribeDeviceEvent(Connection connection, String deviceId, int eventTypeId) {
        String sql = "UPDATE device_event_subscriptions SET enabled = 0, updated_at = CURRENT_TIMESTAMP " +
                "WHERE device_id = ? AND event_type_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, deviceId);
            pstmt.setInt(2, eventTypeId);
            int rows = pstmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            logger.error("取消订阅设备事件失败: deviceId={}, eventTypeId={}", deviceId, eventTypeId, e);
            return false;
        }
    }

    /**
     * 获取设备订阅的事件类型
     */
    public static List<Map<String, Object>> getDeviceSubscriptions(Connection connection, String deviceId) {
        List<Map<String, Object>> subscriptions = new ArrayList<>();
        String sql = "SELECT s.*, e.brand, e.event_code, e.event_name, e.event_name_en, e.category " +
                "FROM device_event_subscriptions s " +
                "JOIN camera_event_types e ON s.event_type_id = e.id " +
                "WHERE s.device_id = ? AND s.enabled = 1";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, deviceId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> subscription = new HashMap<>();
                subscription.put("id", rs.getInt("id"));
                subscription.put("deviceId", rs.getString("device_id"));
                subscription.put("eventTypeId", rs.getInt("event_type_id"));
                subscription.put("enabled", rs.getInt("enabled") == 1);
                subscription.put("mqttForward", rs.getInt("mqtt_forward") == 1);
                subscription.put("brand", rs.getString("brand"));
                subscription.put("eventCode", rs.getInt("event_code"));
                subscription.put("eventName", rs.getString("event_name"));
                subscription.put("eventNameEn", rs.getString("event_name_en"));
                subscription.put("category", rs.getString("category"));
                subscriptions.add(subscription);
            }
        } catch (SQLException e) {
            logger.error("查询设备订阅失败: deviceId={}", deviceId, e);
        }
        return subscriptions;
    }

    /**
     * 检查设备是否订阅了指定事件
     */
    public static boolean isEventSubscribed(Connection connection, String deviceId, String brand, int eventCode) {
        String sql = "SELECT COUNT(*) FROM device_event_subscriptions s " +
                "JOIN camera_event_types e ON s.event_type_id = e.id " +
                "WHERE s.device_id = ? AND e.brand = ? AND e.event_code = ? AND s.enabled = 1";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, deviceId);
            pstmt.setString(2, brand);
            pstmt.setInt(3, eventCode);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            logger.error("检查事件订阅失败: deviceId={}, brand={}, eventCode={}", deviceId, brand, eventCode, e);
        }
        return false;
    }

    /**
     * 批量订阅设备的所有事件类型（按品牌）
     */
    public static int subscribeAllEventsByBrand(Connection connection, String deviceId, String brand) {
        String sql = "INSERT OR IGNORE INTO device_event_subscriptions (device_id, event_type_id, enabled, mqtt_forward) "
                +
                "SELECT ?, id, 1, 1 FROM camera_event_types WHERE brand = ? AND enabled = 1";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, deviceId);
            pstmt.setString(2, brand);
            int rows = pstmt.executeUpdate();
            logger.info("批量订阅设备事件: deviceId={}, brand={}, 订阅数={}", deviceId, brand, rows);
            return rows;
        } catch (SQLException e) {
            logger.error("批量订阅设备事件失败: deviceId={}, brand={}", deviceId, brand, e);
            return 0;
        }
    }

    // ==================== 辅助方法 ====================

    private static Map<String, Object> mapResultSetToEventType(ResultSet rs) throws SQLException {
        Map<String, Object> eventType = new HashMap<>();
        eventType.put("id", rs.getInt("id"));
        eventType.put("brand", rs.getString("brand"));
        eventType.put("eventCode", rs.getInt("event_code"));
        eventType.put("eventName", rs.getString("event_name"));
        eventType.put("eventNameEn", rs.getString("event_name_en"));
        eventType.put("category", rs.getString("category"));
        eventType.put("description", rs.getString("description"));
        eventType.put("enabled", rs.getInt("enabled") == 1);
        return eventType;
    }
}
