package com.digital.video.gateway.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
 * 标准事件表管理类
 * 存储全局唯一的标准事件定义，与品牌无关
 */
public class CanonicalEventTable {
    private static final Logger logger = LoggerFactory.getLogger(CanonicalEventTable.class);

    /**
     * 创建标准事件表
     */
    public static void createTables(Connection connection) throws SQLException {
        String createCanonicalEventsTable = "CREATE TABLE IF NOT EXISTS canonical_events (" +
                "id SERIAL PRIMARY KEY, " +
                "event_id INTEGER UNIQUE, " + // 网关事件编号 1000～2000，与 mqtt-alarm-event-ids.csv 一致
                "event_key TEXT UNIQUE NOT NULL, " + // 标准事件键（全局唯一，如 PERIMETER_INTRUSION）
                "name_zh TEXT NOT NULL, " +
                "name_en TEXT, " +
                "category TEXT DEFAULT 'vca', " +
                "description TEXT, " +
                "severity TEXT DEFAULT 'warning', " +
                "enabled INTEGER DEFAULT 1, " +
                "created_at TIMESTAMP DEFAULT NOW(), " +
                "updated_at TIMESTAMP DEFAULT NOW()" +
                ")";

        String createBrandMappingTable = "CREATE TABLE IF NOT EXISTS brand_event_mapping (" +
                "id SERIAL PRIMARY KEY, " +
                "brand TEXT NOT NULL, " + // 品牌: tiandy, hikvision
                "source_kind TEXT NOT NULL, " + // 源类型: alarm_type, vca_event, alarm_type_ext, command
                "source_code INTEGER NOT NULL, " + // 源代码（iAlarmType, iEventType, lCommand等）
                "event_key TEXT NOT NULL, " + // 映射到的标准事件键
                "priority INTEGER DEFAULT 0, " + // 优先级（当同一source_code有多个映射时，优先级高的优先）
                "note TEXT, " + // 备注说明
                "enabled INTEGER DEFAULT 1, " + // 是否可用
                "created_at TIMESTAMP DEFAULT NOW(), " +
                "FOREIGN KEY (event_key) REFERENCES canonical_events(event_key), " +
                "UNIQUE(brand, source_kind, source_code, event_key)" +
                ")";

        String createIndex = "CREATE INDEX IF NOT EXISTS idx_canonical_events_key ON canonical_events(event_key); " +
                "CREATE INDEX IF NOT EXISTS idx_canonical_events_category ON canonical_events(category); " +
                "CREATE INDEX IF NOT EXISTS idx_brand_mapping_brand ON brand_event_mapping(brand); " +
                "CREATE INDEX IF NOT EXISTS idx_brand_mapping_source ON brand_event_mapping(brand, source_kind, source_code); " +
                "CREATE INDEX IF NOT EXISTS idx_brand_mapping_event_key ON brand_event_mapping(event_key);";

        String createRawPayloadTable = "CREATE TABLE IF NOT EXISTS event_raw_payload (" +
                "id SERIAL PRIMARY KEY, " +
                "event_key TEXT NOT NULL, " +
                "brand TEXT NOT NULL, " +
                "raw_payload TEXT, " +
                "created_at TIMESTAMP DEFAULT NOW(), " +
                "UNIQUE(event_key, brand)" +
                ")";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createCanonicalEventsTable);
            stmt.execute(createBrandMappingTable);
            stmt.execute(createRawPayloadTable);
            stmt.execute(createIndex);
            logger.info("标准事件表和品牌映射表创建成功");
            // 初始化默认标准事件（仅当表为空时）
            initDefaultCanonicalEvents(connection);
            // 已有表可能缺少 event_id 列，尝试添加并回填；或新表已有 event_id 由 init 写入
            ensureEventIdColumnAndBackfill(connection);
            // 确保 GIS_INFO_UPLOAD 事件存在（含已有库迁移）
            ensureGisInfoUploadEvent(connection);
            // 迁移：为已有表添加 is_generic、ai_verify_prompt 列
            ensureNewColumns(connection);
        }
    }

    /**
     * 确保 GIS_INFO_UPLOAD 标准事件及海康映射存在（云台操作会上报该事件，需入事件库以便规则可显式配置）
     */
    public static void ensureGisInfoUploadEvent(Connection connection) throws SQLException {
        String insertEvent = "INSERT INTO canonical_events (event_id, event_key, name_zh, name_en, category, description, severity) VALUES (1122, 'GIS_INFO_UPLOAD', 'GIS信息上传', 'GIS Info Upload', 'basic', '云台/球机上报GIS位置信息', 'info')";
        String insertMapping = "INSERT INTO brand_event_mapping (brand, source_kind, source_code, event_key, priority, note) VALUES ('hikvision', 'command', 16402, 'GIS_INFO_UPLOAD', 0, 'GIS信息上传 (COMM_GISINFO_UPLOAD 0x4012)')";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(insertEvent);
            stmt.execute(insertMapping);
        }
    }

    /**
     * 为 canonical_events 添加 event_id 列并按最新 docs/mqtt-alarm-event-ids.csv 回填/对齐
     */
    public static void ensureEventIdColumnAndBackfill(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            try {
                stmt.execute("ALTER TABLE canonical_events ADD COLUMN event_id INTEGER");
                logger.info("已为 canonical_events 添加 event_id 列");
            } catch (SQLException e) {
                if (!e.getMessage().toLowerCase().contains("duplicate column")) {
                    logger.debug("event_id 列可能已存在: {}", e.getMessage());
                }
            }
            try {
                stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_canonical_events_event_id ON canonical_events(event_id)");
            } catch (SQLException e) {
                logger.debug("event_id 唯一索引可能已存在: {}", e.getMessage());
            }
        }
        backfillEventIdFromCsv(connection);
    }

    /**
     * 从 mqtt-alarm-event-ids.csv 加载并回填/更新 canonical_events 的 event_id 及名称等，与最新 CSV 对齐。
     * 优先读取 docs/mqtt-alarm-event-ids.csv、../docs/mqtt-alarm-event-ids.csv，其次 classpath /mqtt-alarm-event-ids.csv
     */
    public static void backfillEventIdFromCsv(Connection connection) throws SQLException {
        BufferedReader reader = null;
        Path docsPath = Paths.get(System.getProperty("user.dir", ".")).resolve("docs").resolve("mqtt-alarm-event-ids.csv");
        Path docsPathParent = Paths.get(System.getProperty("user.dir", ".")).resolve("..").resolve("docs").resolve("mqtt-alarm-event-ids.csv");
        try {
            if (Files.isRegularFile(docsPath)) {
                reader = new BufferedReader(new InputStreamReader(Files.newInputStream(docsPath), StandardCharsets.UTF_8));
                logger.info("从文件加载 event_id 表: {}", docsPath.toAbsolutePath());
            } else if (Files.isRegularFile(docsPathParent)) {
                reader = new BufferedReader(new InputStreamReader(Files.newInputStream(docsPathParent), StandardCharsets.UTF_8));
                logger.info("从文件加载 event_id 表: {}", docsPathParent.toAbsolutePath());
            } else {
                java.io.InputStream in = CanonicalEventTable.class.getResourceAsStream("/mqtt-alarm-event-ids.csv");
                if (in != null) {
                    reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    logger.info("从 classpath 加载 event_id 表: mqtt-alarm-event-ids.csv");
                }
            }
            if (reader == null) {
                logger.warn("未找到 mqtt-alarm-event-ids.csv，跳过 event_id 回填");
                return;
            }
            String updateSql = "UPDATE canonical_events SET event_id = ?, name_zh = ?, name_en = ?, category = ?, severity = ?, description = ?, updated_at = NOW() WHERE event_key = ?";
            String insertSql = "INSERT INTO canonical_events (event_id, event_key, name_zh, name_en, category, severity, description) VALUES (?, ?, ?, ?, ?, ?, ?)";
            int updated = 0, inserted = 0;
            String line = reader.readLine();
            if (line == null || !line.trim().toLowerCase().startsWith("event_id")) {
                logger.warn("CSV 无表头或格式异常，跳过");
                return;
            }
            try (PreparedStatement pstmtUpdate = connection.prepareStatement(updateSql);
                 PreparedStatement pstmtInsert = connection.prepareStatement(insertSql)) {
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    String[] parts = line.split(",", 7);
                    if (parts.length < 7) continue;
                    int eventId;
                    try {
                        eventId = Integer.parseInt(parts[0].trim());
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    String eventKey = parts[1].trim();
                    String nameZh = parts[2].trim();
                    String nameEn = parts[3].trim();
                    String category = parts[4].trim();
                    String severity = parts[5].trim();
                    String description = parts.length > 6 ? parts[6].trim() : "";
                    pstmtUpdate.setInt(1, eventId);
                    pstmtUpdate.setString(2, nameZh);
                    pstmtUpdate.setString(3, nameEn);
                    pstmtUpdate.setString(4, category);
                    pstmtUpdate.setString(5, severity);
                    pstmtUpdate.setString(6, description);
                    pstmtUpdate.setString(7, eventKey);
                    int n = pstmtUpdate.executeUpdate();
                    if (n > 0) {
                        updated++;
                    } else {
                        pstmtInsert.setInt(1, eventId);
                        pstmtInsert.setString(2, eventKey);
                        pstmtInsert.setString(3, nameZh);
                        pstmtInsert.setString(4, nameEn);
                        pstmtInsert.setString(5, category);
                        pstmtInsert.setString(6, severity);
                        pstmtInsert.setString(7, description);
                        pstmtInsert.executeUpdate();
                        inserted++;
                    }
                }
            }
            logger.info("canonical_events event_id 回填完成: 更新 {} 条，新增 {} 条（与 mqtt-alarm-event-ids.csv 对齐）", updated, inserted);
        } catch (IOException e) {
            logger.warn("读取 mqtt-alarm-event-ids.csv 失败: {}，跳过 event_id 回填", e.getMessage());
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * 初始化默认标准事件
     */
    private static void initDefaultCanonicalEvents(Connection connection) throws SQLException {
        // 检查是否已有数据
        String checkSql = "SELECT COUNT(*) FROM canonical_events";
        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery(checkSql)) {
            if (rs.next() && rs.getInt(1) > 0) {
                logger.debug("标准事件数据已存在，跳过初始化");
                return;
            }
        }

        String sql = "INSERT INTO canonical_events (event_id, event_key, name_zh, name_en, category, description, severity) VALUES (?, ?, ?, ?, ?, ?, ?)";

        // 定义标准事件（event_id 与 docs/mqtt-alarm-event-ids.csv 一致，1000～2000）
        Object[][] canonicalEvents = {
                // 基础 1000～1008
                { 1000, "MOTION_DETECTION", "移动侦测", "Motion Detection", "basic", "视频画面移动侦测报警", "warning" },
                { 1001, "VIDEO_LOST", "视频丢失", "Video Lost", "basic", "视频信号丢失报警", "error" },
                { 1002, "VIDEO_TAMPER", "视频遮挡", "Video Tamper", "basic", "视频遮挡/遮盖报警", "warning" },
                { 1003, "ALARM_INPUT", "开关量输入", "Alarm Input", "basic", "外部开关量输入触发", "warning" },
                { 1004, "ALARM_OUTPUT", "开关量输出", "Alarm Output", "basic", "外部开关量输出触发", "info" },
                { 1005, "AUDIO_LOST", "音频丢失", "Audio Lost", "basic", "音频信号丢失报警", "warning" },
                { 1006, "DEVICE_EXCEPTION", "设备异常", "Device Exception", "basic", "设备异常报警", "error" },
                { 1007, "RECORDING_ALARM", "录像报警", "Recording Alarm", "basic", "录像状态异常报警", "warning" },
                { 1008, "UNIQUE_ALERT", "特色警戒报警", "Unique Alert Alarm", "basic", "特色警戒报警消息", "warning" },
                // 智能分析 1100～1121
                { 1100, "LINE_CROSSING", "单绊线越界", "Line Crossing", "vca", "单绊线越界检测", "warning" },
                { 1101, "DOUBLE_LINE_CROSSING", "双绊线越界", "Double Line Crossing", "vca", "双绊线越界检测", "warning" },
                { 1102, "PERIMETER_INTRUSION", "周界入侵", "Perimeter Intrusion", "vca", "周界/区域入侵检测", "warning" },
                { 1103, "LOITERING", "徘徊检测", "Loitering", "vca", "人员徘徊检测", "warning" },
                { 1104, "PARKING_DETECTION", "停车检测", "Parking Detection", "vca", "违规停车检测", "warning" },
                { 1105, "RUNNING_DETECTION", "快速奔跑", "Running Detection", "vca", "人员快速奔跑检测", "warning" },
                { 1106, "CROWD_DENSITY", "区域人员密度", "Crowd Density", "vca", "区域人员密度统计", "info" },
                { 1107, "OBJECT_LEFT", "物品遗弃", "Object Left", "vca", "物品遗留检测", "warning" },
                { 1108, "OBJECT_REMOVAL", "物品遗失", "Object Removal", "vca", "物品被拿走/遗失检测", "warning" },
                { 1109, "FACE_RECOGNITION", "人脸识别", "Face Recognition", "face", "人脸检测与识别", "info" },
                { 1110, "VIDEO_DIAGNOSIS", "视频诊断", "Video Diagnosis", "vca", "画面异常诊断", "warning" },
                { 1111, "INTELLIGENT_TRACKING", "智能跟踪", "Intelligent Tracking", "vca", "目标自动跟踪", "info" },
                { 1112, "TRAFFIC_STATISTICS", "交通统计", "Traffic Statistics", "its", "车流/人流量统计", "info" },
                { 1113, "CROWD_GATHERING", "人群聚集", "Crowd Gathering", "vca", "人群异常聚集检测", "warning" },
                { 1114, "ABSENCE_DETECTION", "离岗检测", "Absence Detection", "vca", "岗位无人/离岗检测", "warning" },
                { 1115, "WATER_LEVEL_DETECTION", "水位监测", "Water Level Detection", "vca", "水位监测", "warning" },
                { 1116, "AUDIO_DIAGNOSIS", "音频诊断", "Audio Diagnosis", "vca", "声音异常检测", "warning" },
                { 1117, "BEHAVIOR_ANALYSIS", "行为分析", "Behavior Analysis", "vca", "异常行为检测信息", "warning" },
                { 1118, "REGION_ENTRANCE", "进入区域", "Region Entrance", "vca", "目标进入指定区域", "warning" },
                { 1119, "REGION_EXITING", "离开区域", "Region Exiting", "vca", "目标离开指定区域", "info" },
                { 1120, "FALL_DETECTION", "倒地检测", "Fall Detection", "vca", "人员倒地检测", "critical" },
                { 1121, "PLAYING_PHONE", "玩手机", "Playing Phone", "vca", "玩手机检测", "warning" },
                { 1122, "GIS_INFO_UPLOAD", "GIS信息上传", "GIS Info Upload", "basic", "云台/球机上报GIS位置信息（非安防报警，通常不需触发工作流）", "info" },
        };

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            for (Object[] event : canonicalEvents) {
                pstmt.setInt(1, (Integer) event[0]);
                pstmt.setString(2, (String) event[1]);
                pstmt.setString(3, (String) event[2]);
                pstmt.setString(4, (String) event[3]);
                pstmt.setString(5, (String) event[4]);
                pstmt.setString(6, (String) event[5]);
                pstmt.setString(7, (String) event[6]);
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            logger.info("标准事件初始化完成: {} 条", canonicalEvents.length);
        }
        
        // 初始化品牌映射数据
        initBrandMappings(connection);
    }
    
    /**
     * 初始化品牌映射数据
     */
    private static void initBrandMappings(Connection connection) throws SQLException {
        // 检查是否已有数据
        String checkSql = "SELECT COUNT(*) FROM brand_event_mapping";
        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery(checkSql)) {
            if (rs.next() && rs.getInt(1) > 0) {
                logger.debug("品牌映射数据已存在，跳过初始化");
                return;
            }
        }
        
        String sql = "INSERT INTO brand_event_mapping (brand, source_kind, source_code, event_key, priority, note) VALUES (?, ?, ?, ?, ?, ?)";
        
        // 天地伟业映射数据
        Object[][] tiandyMappings = {
                // ========== 基础报警类型映射 (iAlarmType) ==========
                { "tiandy", "alarm_type", 0, "MOTION_DETECTION", 0, "移动侦测 (ALARM_VDO_MOTION)" },
                { "tiandy", "alarm_type", 1, "RECORDING_ALARM", 0, "录像报警 (ALARM_VDO_REC)" },
                { "tiandy", "alarm_type", 2, "VIDEO_LOST", 0, "视频丢失 (ALARM_VDO_LOST)" },
                { "tiandy", "alarm_type", 3, "ALARM_INPUT", 0, "开关量输入 (ALARM_VDO_INPORT)" },
                { "tiandy", "alarm_type", 4, "ALARM_OUTPUT", 0, "开关量输出 (ALARM_VDO_OUTPORT)" },
                { "tiandy", "alarm_type", 5, "VIDEO_TAMPER", 0, "视频遮挡 (ALARM_VDO_COVER)" },
                { "tiandy", "alarm_type", 7, "AUDIO_LOST", 0, "音频丢失 (ALARM_AUDIO_LOST)" },
                { "tiandy", "alarm_type", 8, "DEVICE_EXCEPTION", 0, "设备异常 (ALARM_EXCEPTION)" },
                { "tiandy", "alarm_type", 10, "UNIQUE_ALERT", 0, "特色警戒报警 (ALARM_UNIQUE_ALERT_MSG)" },
                
                // ========== 智能分析事件映射 (iEventType) - 优先级高于alarm_type ==========
                { "tiandy", "vca_event", 0, "LINE_CROSSING", 10, "单绊线越界 (VCA_EVENT_TRIPWIRE)" },
                { "tiandy", "vca_event", 1, "DOUBLE_LINE_CROSSING", 10, "双绊线越界 (VCA_EVENT_DBTRIPWIRE)" },
                { "tiandy", "vca_event", 2, "PERIMETER_INTRUSION", 10, "周界入侵 (VCA_EVENT_PERIMETER)" },
                { "tiandy", "vca_event", 3, "LOITERING", 10, "徘徊检测 (VCA_EVENT_LOITER)" },
                { "tiandy", "vca_event", 4, "PARKING_DETECTION", 10, "停车检测 (VCA_EVENT_PARKING)" },
                { "tiandy", "vca_event", 5, "RUNNING_DETECTION", 10, "快速奔跑 (VCA_EVENT_RUN)" },
                { "tiandy", "vca_event", 6, "CROWD_DENSITY", 10, "区域人员密度 (VCA_EVENT_HIGH_DENSITY)" },
                { "tiandy", "vca_event", 7, "OBJECT_LEFT", 10, "物品遗弃 (VCA_EVENT_ABANDUM)" },
                { "tiandy", "vca_event", 8, "OBJECT_REMOVAL", 10, "物品遗失 (VCA_EVENT_OBJSTOLEN)" },
                { "tiandy", "vca_event", 9, "FACE_RECOGNITION", 10, "人脸识别 (VCA_EVENT_FACEREC)" },
                { "tiandy", "vca_event", 10, "VIDEO_DIAGNOSIS", 10, "视频诊断 (VCA_EVENT_VIDEODETECT)" },
                { "tiandy", "vca_event", 11, "INTELLIGENT_TRACKING", 10, "智能跟踪 (VCA_EVENT_TRACK)" },
                { "tiandy", "vca_event", 12, "TRAFFIC_STATISTICS", 10, "交通统计 (VCA_EVENT_FLUXSTATISTIC)" },
                { "tiandy", "vca_event", 13, "CROWD_GATHERING", 10, "人群聚集 (VCA_EVENT_CROWD)" },
                { "tiandy", "vca_event", 14, "ABSENCE_DETECTION", 10, "离岗检测 (VCA_EVENT_LEAVE_DETECT)" },
                { "tiandy", "vca_event", 15, "WATER_LEVEL_DETECTION", 10, "水位监测 (VCA_EVENT_WATER_LEVEL_DETECT)" },
                { "tiandy", "vca_event", 16, "AUDIO_DIAGNOSIS", 10, "音频诊断 (VCA_EVENT_AUDIO_DIAGNOSE)" },
        };
        
        // 海康映射数据（仅视频+VCA相关）
        Object[][] hikvisionMappings = {
                // ========== 基础报警类型映射 (COMM_ALARM/V30/V40) ==========
                { "hikvision", "command", 0x1100, "MOTION_DETECTION", 0, "移动侦测 (COMM_ALARM)" },
                { "hikvision", "command", 0x4000, "MOTION_DETECTION", 0, "移动侦测 (COMM_ALARM_V30)" },
                { "hikvision", "command", 0x4007, "MOTION_DETECTION", 0, "移动侦测 (COMM_ALARM_V40)" },
                
                // ========== 基础报警类型映射 (dwAlarmType) ==========
                { "hikvision", "alarm_type", 0, "ALARM_INPUT", 0, "信号量报警 (IO信号量报警)" },
                { "hikvision", "alarm_type", 1, "DEVICE_EXCEPTION", 0, "硬盘满" },
                { "hikvision", "alarm_type", 2, "VIDEO_LOST", 0, "信号丢失 (视频信号丢失)" },
                { "hikvision", "alarm_type", 3, "MOTION_DETECTION", 0, "移动侦测" },
                { "hikvision", "alarm_type", 4, "DEVICE_EXCEPTION", 0, "硬盘未格式化" },
                { "hikvision", "alarm_type", 5, "DEVICE_EXCEPTION", 0, "读写硬盘出错" },
                { "hikvision", "alarm_type", 6, "VIDEO_TAMPER", 0, "遮挡报警" },
                { "hikvision", "alarm_type", 7, "DEVICE_EXCEPTION", 0, "制式不匹配" },
                { "hikvision", "alarm_type", 8, "DEVICE_EXCEPTION", 0, "非法访问" },
                
                // ========== 智能分析事件映射 (COMM_ALARM_RULE - wEventTypeEx) ==========
                { "hikvision", "vca_event", 1, "LINE_CROSSING", 10, "穿越警戒面/越界侦测" },
                { "hikvision", "vca_event", 2, "REGION_ENTRANCE", 10, "目标进入区域" },
                { "hikvision", "vca_event", 3, "REGION_EXITING", 10, "目标离开区域" },
                { "hikvision", "vca_event", 4, "PERIMETER_INTRUSION", 10, "周界入侵" },
                { "hikvision", "vca_event", 5, "LOITERING", 10, "徘徊" },
                { "hikvision", "vca_event", 8, "RUNNING_DETECTION", 10, "快速移动/奔跑" },
                { "hikvision", "vca_event", 15, "ABSENCE_DETECTION", 10, "离岗检测" },
                { "hikvision", "vca_event", 20, "FALL_DETECTION", 10, "倒地检测" },
                { "hikvision", "vca_event", 44, "PLAYING_PHONE", 10, "玩手机" },
                
                // ========== 智能检测通用报警 (COMM_VCA_ALARM) ==========
                { "hikvision", "command", 0x4993, "BEHAVIOR_ANALYSIS", 0, "智能检测通用报警 (COMM_VCA_ALARM)" },
                // ========== GIS/云台信息上传（操作云台时上报，非安防报警） ==========
                { "hikvision", "command", 0x4012, "GIS_INFO_UPLOAD", 0, "GIS信息上传 (COMM_GISINFO_UPLOAD)" },
        };
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            // 插入天地伟业映射
            for (Object[] mapping : tiandyMappings) {
                pstmt.setString(1, (String) mapping[0]);
                pstmt.setString(2, (String) mapping[1]);
                pstmt.setInt(3, (Integer) mapping[2]);
                pstmt.setString(4, (String) mapping[3]);
                pstmt.setInt(5, (Integer) mapping[4]);
                pstmt.setString(6, (String) mapping[5]);
                pstmt.addBatch();
            }
            
            // 插入海康映射
            for (Object[] mapping : hikvisionMappings) {
                pstmt.setString(1, (String) mapping[0]);
                pstmt.setString(2, (String) mapping[1]);
                pstmt.setInt(3, (Integer) mapping[2]);
                pstmt.setString(4, (String) mapping[3]);
                pstmt.setInt(5, (Integer) mapping[4]);
                pstmt.setString(6, (String) mapping[5]);
                pstmt.addBatch();
            }
            
            pstmt.executeBatch();
            logger.info("品牌映射初始化完成: 天地伟业 {} 条, 海康 {} 条", tiandyMappings.length, hikvisionMappings.length);
        }
    }

    /**
     * 从 mqtt-alarm-event-ids.csv 按 event_key 查找 event_id 与中英文名称（DB 无记录时的兜底）
     */
    public static Map<String, Object> getEventIdAndNamesFromCsv(String eventKey) {
        if (eventKey == null || eventKey.isEmpty()) return null;
        BufferedReader reader = null;
        Path docsPath = Paths.get(System.getProperty("user.dir", ".")).resolve("docs").resolve("mqtt-alarm-event-ids.csv");
        Path docsPathParent = Paths.get(System.getProperty("user.dir", ".")).resolve("..").resolve("docs").resolve("mqtt-alarm-event-ids.csv");
        try {
            if (Files.isRegularFile(docsPath)) {
                reader = new BufferedReader(new InputStreamReader(Files.newInputStream(docsPath), StandardCharsets.UTF_8));
            } else if (Files.isRegularFile(docsPathParent)) {
                reader = new BufferedReader(new InputStreamReader(Files.newInputStream(docsPathParent), StandardCharsets.UTF_8));
            } else {
                java.io.InputStream in = CanonicalEventTable.class.getResourceAsStream("/mqtt-alarm-event-ids.csv");
                if (in != null) {
                    reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                }
            }
            if (reader == null) return null;
            String line = reader.readLine();
            if (line == null || !line.trim().toLowerCase().startsWith("event_id")) return null;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split(",", 7);
                if (parts.length < 4) continue;
                try {
                    Integer.parseInt(parts[0].trim());
                } catch (NumberFormatException e) {
                    continue;
                }
                String key = parts[1].trim();
                if (!eventKey.equals(key)) continue;
                int eventId = Integer.parseInt(parts[0].trim());
                String nameZh = parts[2].trim();
                String nameEn = parts[3].trim();
                Map<String, Object> out = new HashMap<>();
                out.put("eventId", eventId);
                out.put("nameZh", nameZh);
                out.put("nameEn", nameEn);
                return out;
            }
        } catch (IOException | NumberFormatException e) {
            logger.debug("从 CSV 按 event_key 查找失败: eventKey={}, {}", eventKey, e.getMessage());
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                }
            }
        }
        return null;
    }

    /**
     * 根据event_key获取标准事件
     */
    public static Map<String, Object> getCanonicalEvent(Connection connection, String eventKey) {
        String sql = "SELECT * FROM canonical_events WHERE event_key = ? AND enabled = 1 LIMIT 1";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, eventKey);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return mapResultSetToEvent(rs);
            }
        } catch (SQLException e) {
            logger.error("查询标准事件失败: eventKey={}", eventKey, e);
        }
        return null;
    }

    /**
     * 获取所有标准事件
     */
    public static List<Map<String, Object>> getAllCanonicalEvents(Connection connection) {
        List<Map<String, Object>> events = new ArrayList<>();
        String sql = "SELECT * FROM canonical_events WHERE enabled = 1 ORDER BY category, event_key";
        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                events.add(mapResultSetToEvent(rs));
            }
        } catch (SQLException e) {
            logger.error("查询所有标准事件失败", e);
        }
        return events;
    }

    /**
     * 获取所有标准事件并附带关联品牌列表
     * 每个事件增加 brands 字段：该事件在 brand_event_mapping 中被哪些品牌映射
     */
    public static List<Map<String, Object>> getAllCanonicalEventsWithBrands(Connection connection) {
        List<Map<String, Object>> events = getAllCanonicalEvents(connection);
        if (events.isEmpty()) return events;

        // 一次性查出所有映射的 event_key -> brands
        Map<String, List<String>> eventBrandsMap = new HashMap<>();
        String sql = "SELECT DISTINCT event_key, brand FROM brand_event_mapping WHERE enabled = 1 ORDER BY event_key, brand";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String ek = rs.getString("event_key");
                String brand = rs.getString("brand");
                eventBrandsMap.computeIfAbsent(ek, k -> new ArrayList<>()).add(brand);
            }
        } catch (SQLException e) {
            logger.error("查询事件品牌映射失败", e);
        }

        for (Map<String, Object> event : events) {
            String eventKey = (String) event.get("eventKey");
            List<String> brands = eventBrandsMap.getOrDefault(eventKey, new ArrayList<>());
            event.put("brands", brands);
        }
        return events;
    }

    /**
     * 根据品牌、源类型、源代码查找映射的标准事件
     */
    public static Map<String, Object> resolveEvent(Connection connection, String brand, String sourceKind, int sourceCode) {
        String sql = "SELECT e.* FROM canonical_events e " +
                "JOIN brand_event_mapping m ON e.event_key = m.event_key " +
                "WHERE m.brand = ? AND m.source_kind = ? AND m.source_code = ? AND m.enabled = 1 AND e.enabled = 1 " +
                "ORDER BY m.priority DESC LIMIT 1";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, brand.toLowerCase());
            pstmt.setString(2, sourceKind);
            pstmt.setInt(3, sourceCode);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return mapResultSetToEvent(rs);
            }
        } catch (SQLException e) {
            logger.error("解析事件失败: brand={}, sourceKind={}, sourceCode={}", brand, sourceKind, sourceCode, e);
        }
        return null;
    }

    /**
     * 获取品牌的所有映射
     */
    public static List<Map<String, Object>> getBrandMappings(Connection connection, String brand) {
        List<Map<String, Object>> mappings = new ArrayList<>();
        String sql = "SELECT m.*, e.name_zh, e.name_en, e.category " +
                "FROM brand_event_mapping m " +
                "JOIN canonical_events e ON m.event_key = e.event_key " +
                "WHERE m.brand = ? AND m.enabled = 1 " +
                "ORDER BY m.source_kind, m.source_code";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, brand.toLowerCase());
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> mapping = new HashMap<>();
                mapping.put("id", rs.getInt("id"));
                mapping.put("brand", rs.getString("brand"));
                mapping.put("sourceKind", rs.getString("source_kind"));
                mapping.put("sourceCode", rs.getInt("source_code"));
                mapping.put("eventKey", rs.getString("event_key"));
                mapping.put("priority", rs.getInt("priority"));
                mapping.put("note", rs.getString("note"));
                mapping.put("eventNameZh", rs.getString("name_zh"));
                mapping.put("eventNameEn", rs.getString("name_en"));
                mapping.put("category", rs.getString("category"));
                mappings.add(mapping);
            }
        } catch (SQLException e) {
            logger.error("查询品牌映射失败: brand={}", brand, e);
        }
        return mappings;
    }

    /**
     * 根据 event_key 查询 event_id（1000～2000），报警上报时使用
     */
    public static Integer getEventIdByEventKey(Connection connection, String eventKey) {
        String sql = "SELECT event_id FROM canonical_events WHERE event_key = ? AND enabled = 1 LIMIT 1";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, eventKey);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                int id = rs.getInt("event_id");
                return rs.wasNull() ? null : id;
            }
        } catch (SQLException e) {
            logger.error("查询 event_id 失败: eventKey={}", eventKey, e);
        }
        return null;
    }

    /**
     * 迁移：为已有 canonical_events 表添加 is_generic、ai_verify_prompt 列（列已存在则忽略）
     */
    public static void ensureNewColumns(Connection connection) throws SQLException {
        String[][] cols = {
            {"is_generic", "ALTER TABLE canonical_events ADD COLUMN is_generic INTEGER DEFAULT 0"},
            {"ai_verify_prompt", "ALTER TABLE canonical_events ADD COLUMN ai_verify_prompt TEXT"}
        };
        try (Statement stmt = connection.createStatement()) {
            for (String[] col : cols) {
                try {
                    stmt.execute(col[1]);
                    logger.info("已为 canonical_events 添加 {} 列", col[0]);
                } catch (SQLException e) {
                    // duplicate column 或已存在，忽略
                    logger.debug("{} 列可能已存在: {}", col[0], e.getMessage());
                }
            }
        }
    }

    // ==================== 未知事件自动入库 ====================

    /**
     * 检查事件是否已存在且标记为通用报警
     */
    public static boolean isGenericEvent(Connection connection, String eventKey) {
        String sql = "SELECT is_generic FROM canonical_events WHERE event_key = ? LIMIT 1";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, eventKey);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("is_generic") == 1;
            }
        } catch (SQLException e) {
            logger.debug("查询 is_generic 失败: eventKey={}, {}", eventKey, e.getMessage());
        }
        return false;
    }

    /**
     * 为未知事件自动入库：若 event_key 已存在且为通用报警则跳过（返回 false），否则 INSERT OR IGNORE。
     * @return true 表示新写入了事件（或已存在的非通用事件）；false 表示跳过（通用报警）
     */
    public static boolean ensureEventForUnknown(Connection connection, String brand, String alarmType, String rawPayloadJson) {
        if (alarmType == null || alarmType.isEmpty()) return false;

        // 已存在且是通用报警 → 跳过，由上层走 SDK 细解析
        if (isGenericEvent(connection, alarmType)) {
            logger.debug("事件已标记为通用报警，跳过自动入库: eventKey={}", alarmType);
            return false;
        }

        // 检查事件是否已存在（无论是否通用）
        boolean eventExists = getCanonicalEvent(connection, alarmType) != null;
        // 也检查 disabled 的
        if (!eventExists) {
            String checkSql = "SELECT COUNT(*) FROM canonical_events WHERE event_key = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(checkSql)) {
                pstmt.setString(1, alarmType);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next() && rs.getInt(1) > 0) eventExists = true;
            } catch (SQLException e) {
                logger.debug("检查事件存在性失败: {}", e.getMessage());
            }
        }

        if (!eventExists) {
            // 写入 canonical_events
            String insertEvent = "INSERT INTO canonical_events (event_key, name_zh, name_en, category, description, severity, is_generic) " +
                    "VALUES (?, ?, ?, 'unknown', ?, 'info', 0)";
            try (PreparedStatement pstmt = connection.prepareStatement(insertEvent)) {
                pstmt.setString(1, alarmType);
                pstmt.setString(2, alarmType); // name_zh 暂用 event_key
                pstmt.setString(3, alarmType); // name_en 暂用 event_key
                pstmt.setString(4, "系统自动发现的未知事件: " + alarmType);
                pstmt.executeUpdate();
                logger.info("自动入库未知事件: eventKey={}, brand={}", alarmType, brand);
            } catch (SQLException e) {
                logger.warn("自动入库事件失败: eventKey={}, {}", alarmType, e.getMessage());
            }

            // 写入 brand_event_mapping
            String insertMapping = "INSERT INTO brand_event_mapping (brand, source_kind, source_code, event_key, priority, note) " +
                    "VALUES (?, 'event_key', 0, ?, 0, ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(insertMapping)) {
                pstmt.setString(1, brand.toLowerCase());
                pstmt.setString(2, alarmType);
                pstmt.setString(3, "系统自动发现 (" + brand + ")");
                pstmt.executeUpdate();
            } catch (SQLException e) {
                logger.debug("自动入库映射失败: {}", e.getMessage());
            }
        }

        // 写入原始报警数据（首次样本，已有则不覆盖）
        if (rawPayloadJson != null && !rawPayloadJson.isEmpty()) {
            insertRawPayload(connection, alarmType, brand, rawPayloadJson);
        }

        return true;
    }

    // ==================== event_raw_payload 操作 ====================

    public static void insertRawPayload(Connection connection, String eventKey, String brand, String rawPayload) {
        String sql = "INSERT INTO event_raw_payload (event_key, brand, raw_payload) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, eventKey);
            pstmt.setString(2, brand.toLowerCase());
            pstmt.setString(3, rawPayload);
            int n = pstmt.executeUpdate();
            if (n > 0) {
                logger.info("写入原始报警数据样本: eventKey={}, brand={}", eventKey, brand);
            }
        } catch (SQLException e) {
            logger.debug("写入原始报警数据失败: eventKey={}, {}", eventKey, e.getMessage());
        }
    }

    public static Map<String, Object> getRawPayload(Connection connection, String eventKey, String brand) {
        String sql = "SELECT * FROM event_raw_payload WHERE event_key = ? AND brand = ? LIMIT 1";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, eventKey);
            pstmt.setString(2, brand.toLowerCase());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", rs.getInt("id"));
                m.put("eventKey", rs.getString("event_key"));
                m.put("brand", rs.getString("brand"));
                m.put("rawPayload", rs.getString("raw_payload"));
                m.put("createdAt", rs.getString("created_at"));
                return m;
            }
        } catch (SQLException e) {
            logger.debug("查询原始报警数据失败: {}", e.getMessage());
        }
        return null;
    }

    public static List<Map<String, Object>> getRawPayloadsByEventKey(Connection connection, String eventKey) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT * FROM event_raw_payload WHERE event_key = ? ORDER BY created_at DESC";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, eventKey);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", rs.getInt("id"));
                m.put("eventKey", rs.getString("event_key"));
                m.put("brand", rs.getString("brand"));
                m.put("rawPayload", rs.getString("raw_payload"));
                m.put("createdAt", rs.getString("created_at"));
                list.add(m);
            }
        } catch (SQLException e) {
            logger.debug("查询原始报警数据失败: {}", e.getMessage());
        }
        return list;
    }

    // ==================== CRUD 扩展 ====================

    /**
     * 按 ID 查询单条事件（含 is_generic、ai_verify_prompt）
     */
    public static Map<String, Object> getCanonicalEventById(Connection connection, int id) {
        String sql = "SELECT * FROM canonical_events WHERE id = ? LIMIT 1";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return mapResultSetToEvent(rs);
            }
        } catch (SQLException e) {
            logger.error("查询标准事件失败: id={}", id, e);
        }
        return null;
    }

    /**
     * 带筛选条件的事件列表（不限 enabled）
     */
    public static List<Map<String, Object>> listCanonicalEvents(Connection connection,
            String eventKey, String category, String brand, Boolean enabled, Boolean isGeneric) {
        List<Map<String, Object>> events = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT DISTINCT e.* FROM canonical_events e ");
        List<Object> params = new ArrayList<>();

        if (brand != null && !brand.isEmpty()) {
            sql.append("JOIN brand_event_mapping m ON e.event_key = m.event_key AND m.brand = ? ");
            params.add(brand.toLowerCase());
        }

        sql.append("WHERE 1=1 ");
        if (eventKey != null && !eventKey.isEmpty()) {
            String pattern = "%" + eventKey.trim() + "%";
            sql.append("AND (e.event_key LIKE ? OR e.name_zh LIKE ? OR e.name_en LIKE ?) ");
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }
        if (category != null && !category.isEmpty()) {
            sql.append("AND e.category = ? ");
            params.add(category);
        }
        if (enabled != null) {
            sql.append("AND e.enabled = ? ");
            params.add(enabled ? 1 : 0);
        }
        if (isGeneric != null) {
            sql.append("AND e.is_generic = ? ");
            params.add(isGeneric ? 1 : 0);
        }
        sql.append("ORDER BY e.category, e.event_key");

        try (PreparedStatement pstmt = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof Integer) pstmt.setInt(i + 1, (Integer) p);
                else pstmt.setString(i + 1, p.toString());
            }
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                events.add(mapResultSetToEvent(rs));
            }
        } catch (SQLException e) {
            logger.error("查询事件列表失败", e);
        }
        return events;
    }

    /**
     * 新增标准事件
     */
    public static int insertCanonicalEvent(Connection connection, String eventKey, String nameZh, String nameEn,
            String category, String severity, String description, boolean isGeneric, String aiVerifyPrompt) throws SQLException {
        String sql = "INSERT INTO canonical_events (event_key, name_zh, name_en, category, severity, description, is_generic, ai_verify_prompt) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, eventKey);
            pstmt.setString(2, nameZh);
            pstmt.setString(3, nameEn != null ? nameEn : "");
            pstmt.setString(4, category != null ? category : "unknown");
            pstmt.setString(5, severity != null ? severity : "info");
            pstmt.setString(6, description != null ? description : "");
            pstmt.setInt(7, isGeneric ? 1 : 0);
            pstmt.setString(8, aiVerifyPrompt);
            pstmt.executeUpdate();
            ResultSet keys = pstmt.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
        }
        return -1;
    }

    /**
     * 更新标准事件
     */
    public static boolean updateCanonicalEvent(Connection connection, int id, String nameZh, String nameEn,
            String category, String severity, String description, Boolean enabled, Boolean isGeneric, String aiVerifyPrompt) throws SQLException {
        StringBuilder sql = new StringBuilder("UPDATE canonical_events SET updated_at = NOW()");
        List<Object> params = new ArrayList<>();
        if (nameZh != null) { sql.append(", name_zh = ?"); params.add(nameZh); }
        if (nameEn != null) { sql.append(", name_en = ?"); params.add(nameEn); }
        if (category != null) { sql.append(", category = ?"); params.add(category); }
        if (severity != null) { sql.append(", severity = ?"); params.add(severity); }
        if (description != null) { sql.append(", description = ?"); params.add(description); }
        if (enabled != null) { sql.append(", enabled = ?"); params.add(enabled ? 1 : 0); }
        if (isGeneric != null) { sql.append(", is_generic = ?"); params.add(isGeneric ? 1 : 0); }
        if (aiVerifyPrompt != null) { sql.append(", ai_verify_prompt = ?"); params.add(aiVerifyPrompt); }
        sql.append(" WHERE id = ?");
        params.add(id);

        try (PreparedStatement pstmt = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof Integer) pstmt.setInt(i + 1, (Integer) p);
                else pstmt.setString(i + 1, p.toString());
            }
            return pstmt.executeUpdate() > 0;
        }
    }

    /**
     * 删除标准事件及其品牌映射和原始数据
     */
    public static boolean deleteCanonicalEvent(Connection connection, int id) throws SQLException {
        // 先查 event_key
        Map<String, Object> event = getCanonicalEventById(connection, id);
        if (event == null) return false;
        String eventKey = (String) event.get("eventKey");

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DELETE FROM event_raw_payload WHERE event_key = '" + eventKey.replace("'", "''") + "'");
            stmt.execute("DELETE FROM brand_event_mapping WHERE event_key = '" + eventKey.replace("'", "''") + "'");
            stmt.execute("DELETE FROM canonical_events WHERE id = " + id);
        }
        return true;
    }

    // ==================== brand_event_mapping CRUD ====================

    public static List<Map<String, Object>> getMappingsByEventKey(Connection connection, String eventKey) {
        List<Map<String, Object>> mappings = new ArrayList<>();
        String sql = "SELECT * FROM brand_event_mapping WHERE event_key = ? ORDER BY brand, source_kind, source_code";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, eventKey);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", rs.getInt("id"));
                m.put("brand", rs.getString("brand"));
                m.put("sourceKind", rs.getString("source_kind"));
                m.put("sourceCode", rs.getInt("source_code"));
                m.put("eventKey", rs.getString("event_key"));
                m.put("priority", rs.getInt("priority"));
                m.put("note", rs.getString("note"));
                m.put("enabled", rs.getInt("enabled") == 1);
                mappings.add(m);
            }
        } catch (SQLException e) {
            logger.error("查询事件映射失败: eventKey={}", eventKey, e);
        }
        return mappings;
    }

    public static int insertMapping(Connection connection, String brand, String sourceKind, int sourceCode,
            String eventKey, int priority, String note) throws SQLException {
        String sql = "INSERT INTO brand_event_mapping (brand, source_kind, source_code, event_key, priority, note) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, brand.toLowerCase());
            pstmt.setString(2, sourceKind);
            pstmt.setInt(3, sourceCode);
            pstmt.setString(4, eventKey);
            pstmt.setInt(5, priority);
            pstmt.setString(6, note);
            pstmt.executeUpdate();
            ResultSet keys = pstmt.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
        }
        return -1;
    }

    public static boolean deleteMapping(Connection connection, int mappingId) throws SQLException {
        String sql = "DELETE FROM brand_event_mapping WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, mappingId);
            return pstmt.executeUpdate() > 0;
        }
    }

    /**
     * 获取事件的 ai_verify_prompt
     */
    public static String getAiVerifyPrompt(Connection connection, String eventKey) {
        String sql = "SELECT ai_verify_prompt FROM canonical_events WHERE event_key = ? AND enabled = 1 LIMIT 1";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, eventKey);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("ai_verify_prompt");
            }
        } catch (SQLException e) {
            logger.debug("查询 ai_verify_prompt 失败: eventKey={}, {}", eventKey, e.getMessage());
        }
        return null;
    }

    private static Map<String, Object> mapResultSetToEvent(ResultSet rs) throws SQLException {
        Map<String, Object> event = new HashMap<>();
        event.put("id", rs.getInt("id"));
        try {
            event.put("eventId", rs.getObject("event_id"));
        } catch (SQLException e) {
            // event_id 列可能尚未存在
        }
        event.put("eventKey", rs.getString("event_key"));
        event.put("nameZh", rs.getString("name_zh"));
        event.put("nameEn", rs.getString("name_en"));
        event.put("category", rs.getString("category"));
        event.put("description", rs.getString("description"));
        event.put("severity", rs.getString("severity"));
        event.put("enabled", rs.getInt("enabled") == 1);
        try {
            event.put("isGeneric", rs.getInt("is_generic") == 1);
        } catch (SQLException e) {
            event.put("isGeneric", false);
        }
        try {
            event.put("aiVerifyPrompt", rs.getString("ai_verify_prompt"));
        } catch (SQLException e) {
            event.put("aiVerifyPrompt", null);
        }
        try {
            event.put("createdAt", rs.getString("created_at"));
            event.put("updatedAt", rs.getString("updated_at"));
        } catch (SQLException e) {
            // ignore
        }
        return event;
    }
}
