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
     * 根据SDK文档 NetClientTypes.h 和 GlobalTypes.h 完整定义
     * 
     * 映射关系说明：
     * 1. iAlarmType (报警回调中的报警类型): 
     *    - 0-5, 7-8, 10: 直接使用 iAlarmType 作为 event_code
     *    - 6, 9: 需要通过 NetClient_VCAGetAlarmInfo 获取 iEventType，然后使用 iEventType 作为 event_code
     * 2. iEventType (智能分析事件类型，从NetClient_VCAGetAlarmInfo获取): 0-131 对应 event_code 0-131
     * 3. ALARM_TYPE_* (扩展报警类型): 12-41 对应 event_code 12-41
     * 
     * 注意：event_code 0-10 可能同时作为 iAlarmType 和 iEventType，但含义不同：
     * - 作为 iAlarmType: 表示基础报警类型（如移动侦测、视频丢失等）
     * - 作为 iEventType: 表示智能分析事件类型（如单绊线越界、周界入侵等）
     * 当 iAlarmType=6 或 9 时，系统会使用 iEventType 来构建最终的 alarmType，所以优先定义 iEventType 的含义
     */
    private static void initTiandyEventTypes(Connection connection) throws SQLException {
        String sql = "INSERT INTO camera_event_types (brand, event_code, event_name, event_name_en, category, description) VALUES (?, ?, ?, ?, ?, ?)";

        Object[][] tiandyEvents = {
                // ========== 重要说明：event_code 0-10 的双重含义 ==========
                // 根据代码逻辑，event_code 0-10 可能同时表示两种含义：
                // 1. 当 iAlarmType != 6 且 != 9 时：event_code = iAlarmType（基础报警类型）
                // 2. 当 iAlarmType = 6 或 9 时：event_code = iEventType（智能分析事件类型）
                // 
                // 由于当 iAlarmType=6 或 9 时，系统会使用 iEventType 来构建最终的 alarmType，
                // 所以 event_code 0-10 优先定义为 iEventType 的含义（智能分析事件类型）。
                // 当 iAlarmType != 6 且 != 9 时，虽然也使用相同的 event_code，但含义不同。
                // 我们通过在描述中同时说明两种含义来解决这个冲突。
                
                // ========== 基础报警类型和智能分析事件类型 (event_code 0-10) ==========
                // 注意：这些 event_code 同时支持两种含义，具体取决于 iAlarmType 的值
                { "tiandy", 0, "单绊线越界/移动侦测", "Line Crossing/Motion Detection", "vca", "智能分析: 单绊线越界检测 (VCA_EVENT_TRIPWIRE, iEventType=0) | 基础报警: 移动侦测 (ALARM_VDO_MOTION, iAlarmType=0)" },
                { "tiandy", 1, "双绊线越界/录像报警", "Double Line Crossing/Recording Alarm", "vca", "智能分析: 双绊线越界检测 (VCA_EVENT_DBTRIPWIRE, iEventType=1) | 基础报警: 录像报警 (ALARM_VDO_REC, iAlarmType=1)" },
                { "tiandy", 2, "周界入侵/视频丢失", "Perimeter Intrusion/Video Lost", "vca", "智能分析: 周界/区域入侵检测 (VCA_EVENT_PERIMETER, iEventType=2) | 基础报警: 视频丢失 (ALARM_VDO_LOST, iAlarmType=2)" },
                { "tiandy", 3, "徘徊检测/开关量输入", "Loitering/Alarm Input", "vca", "智能分析: 人员徘徊检测 (VCA_EVENT_LOITER, iEventType=3) | 基础报警: 开关量输入 (ALARM_VDO_INPORT, iAlarmType=3)" },
                { "tiandy", 4, "停车检测/开关量输出", "Parking Detection/Alarm Output", "vca", "智能分析: 违规停车检测 (VCA_EVENT_PARKING, iEventType=4) | 基础报警: 开关量输出 (ALARM_VDO_OUTPORT, iAlarmType=4)" },
                { "tiandy", 5, "快速奔跑/视频遮挡", "Running Detection/Video Tamper", "vca", "智能分析: 人员快速奔跑检测 (VCA_EVENT_RUN, iEventType=5) | 基础报警: 视频遮挡 (ALARM_VDO_COVER, iAlarmType=5)" },
                { "tiandy", 6, "区域人员密度/智能分析报警", "Crowd Density/VCA Alarm", "vca", "智能分析: 区域人员密度统计 (VCA_EVENT_HIGH_DENSITY, iEventType=6) | 基础报警: 智能分析报警 (ALARM_VCA_INFO, iAlarmType=6，需通过NetClient_VCAGetAlarmInfo获取具体事件类型)" },
                { "tiandy", 7, "物品遗弃/音频丢失", "Object Left/Audio Lost", "vca", "智能分析: 物品遗留检测 (VCA_EVENT_ABANDUM, iEventType=7) | 基础报警: 音频丢失 (ALARM_AUDIO_LOST, iAlarmType=7)" },
                { "tiandy", 8, "物品遗失/设备异常", "Object Removal/Device Exception", "vca", "智能分析: 物品被拿走/遗失检测 (VCA_EVENT_OBJSTOLEN, iEventType=8) | 基础报警: 设备异常 (ALARM_EXCEPTION, iAlarmType=8)" },
                { "tiandy", 9, "人脸识别/智能分析报警扩展", "Face Recognition/VCA Alarm Extended", "face", "智能分析: 人脸检测与识别 (VCA_EVENT_FACEREC, iEventType=9) | 基础报警: 智能分析报警扩展 (ALARM_VCA_INFO_EX, iAlarmType=9，需通过NetClient_VCAGetAlarmInfo获取具体事件类型)" },
                { "tiandy", 10, "视频诊断/特色警戒报警", "Video Diagnosis/Unique Alert Alarm", "vca", "智能分析: 画面异常诊断 (VCA_EVENT_VIDEODETECT, iEventType=10) | 基础报警: 特色警戒报警 (ALARM_UNIQUE_ALERT_MSG, iAlarmType=10)" },
                
                // ========== 智能分析事件类型 (iEventType) - 从NetClient_VCAGetAlarmInfo获取 ==========
                // 参考: GlobalTypes.h VCA_EVENT_* 定义 (0-131)
                // 注意: 当iAlarmType=6或9时，需要通过NetClient_VCAGetAlarmInfo获取iEventType来确定具体事件
                
                // 基础智能分析事件 (11-16)
                { "tiandy", 11, "智能跟踪", "Intelligent Tracking", "vca", "目标自动跟踪 (VCA_EVENT_TRACK, iEventType=11)" },
                { "tiandy", 12, "交通统计", "Traffic Statistics", "its", "车流/人流量统计 (VCA_EVENT_FLUXSTATISTIC, iEventType=12)" },
                { "tiandy", 13, "人群聚集", "Crowd Gathering", "vca", "人群异常聚集检测 (VCA_EVENT_CROWD, iEventType=13)" },
                { "tiandy", 14, "离岗检测", "Absence Detection", "vca", "岗位无人/离岗检测 (VCA_EVENT_LEAVE_DETECT, iEventType=14)" },
                { "tiandy", 15, "水位监测", "Water Level Detection", "vca", "水位监测 (VCA_EVENT_WATER_LEVEL_DETECT, iEventType=15)" },
                { "tiandy", 16, "音频诊断", "Audio Diagnosis", "vca", "声音异常检测 (VCA_EVENT_AUDIO_DIAGNOSE, iEventType=16)" },
                
                // 扩展智能分析事件 (17-131)
                { "tiandy", 17, "人脸遮挡", "Face Mosaic", "face", "人脸遮挡马赛克 (VCA_EVENT_FACE_MOSAIC, iEventType=17)" },
                { "tiandy", 18, "河道漂浮物", "River Clean", "vca", "河道漂浮物检测 (VCA_EVENT_RIVERCLEAN, iEventType=18)" },
                { "tiandy", 19, "盗采盗卸", "Dredge", "vca", "盗采盗卸检测 (VCA_EVENT_DREDGE, iEventType=19)" },
                { "tiandy", 20, "违章停车", "Illegal Parking", "its", "违章停车检测 (VCA_EVENT_ILLEAGEPARK, iEventType=20)" },
                { "tiandy", 21, "打架", "Fight", "vca", "打架行为检测 (VCA_EVENT_FIGHT, iEventType=21)" },
                { "tiandy", 22, "警戒", "Protect", "vca", "警戒检测 (VCA_EVENT_PROTECT, iEventType=22)" },
                { "tiandy", 23, "车牌识别", "License Plate Recognition", "its", "车牌识别 (VCA_EVENT_PLATE_RECOGNISE, iEventType=23)" },
                { "tiandy", 24, "热度图", "Heat Map", "vca", "热度图统计 (VCA_EVENT_HEAT_MAP, iEventType=24)" },
                { "tiandy", 25, "积水监测", "Seepage", "vca", "积水监测 (VCA_EVENT_SEEPER, iEventType=25)" },
                { "tiandy", 26, "翻窗检测", "Window Detection", "vca", "翻窗检测 (VCA_EVENT_WINDOW_DETECTION, iEventType=26)" },
                { "tiandy", 27, "ST人脸识别", "ST Face Recognition", "face", "ST人脸识别 (VCA_EVENT_STFACEADVANCE, iEventType=27)" },
                { "tiandy", 28, "车位看守", "Park Guard", "vca", "车位看守检测 (VCA_EVENT_PARK_GUARD, iEventType=28)" },
                { "tiandy", 30, "安全帽检测", "Helmet Detection", "vca", "安全帽检测 (VCA_EVENT_HELMET, iEventType=30)" },
                { "tiandy", 31, "联动球机跟踪", "Link Dome Track", "vca", "鱼球联动跟踪 (VCA_EVENT_LINK_DOME_TRACK, iEventType=31)" },
                { "tiandy", 32, "闸门检测", "Sluicegate", "vca", "闸门检测 (VCA_EVENT_SLUICEGATE, iEventType=32)" },
                { "tiandy", 33, "颜色跟踪", "Color Track", "vca", "颜色跟踪算法 (VCA_EVENT_COLOR_TRACK, iEventType=33)" },
                { "tiandy", 34, "结构化算法", "Format Type", "vca", "结构化算法 (VCA_EVENT_FORMAT_TYPE, iEventType=34)" },
                { "tiandy", 35, "积水深度", "Sediment", "vca", "积水深度检测 (VCA_EVENT_SEDIMENT, iEventType=35)" },
                { "tiandy", 36, "警戒水位检测", "Alert Water", "vca", "警戒水位检测 (VCA_EVENT_ALERTWATER, iEventType=36)" },
                { "tiandy", 37, "单人询问", "Single Inquiry", "vca", "单人询问/无人看管 (VCA_EVENT_SINGLE_INQUIRY, iEventType=37)" },
                { "tiandy", 38, "攀高", "Climb Up", "vca", "攀高检测 (VCA_EVENT_CLIMB_UP, iEventType=38)" },
                { "tiandy", 39, "新离岗", "Net Departure", "vca", "新离岗检测 (VCA_EVENT_NET_DEPARTURE, iEventType=39)" },
                { "tiandy", 40, "人数异常", "Abnormal Number", "vca", "人数异常检测 (VCA_EVENT_ABNORMAL_NUMBER, iEventType=40)" },
                { "tiandy", 41, "人员起身", "Get Up", "vca", "人员起身检测 (VCA_EVENT_GET_UP, iEventType=41)" },
                { "tiandy", 42, "离床", "Leave Bed", "vca", "离床检测 (VCA_EVENT_LEAVE_BED, iEventType=42)" },
                { "tiandy", 43, "静止检测", "Static Detection", "vca", "静止检测 (VCA_EVENT_STATIC_DETECTION, iEventType=43)" },
                { "tiandy", 44, "睡岗", "Sleep Position", "vca", "睡岗检测 (VCA_EVENT_SLEEP_POSTION, iEventType=44)" },
                { "tiandy", 45, "摔倒", "Slip Up", "vca", "摔倒检测 (VCA_EVENT_SLIP_UP, iEventType=45)" },
                { "tiandy", 46, "新打架", "New Fight", "vca", "新打架检测 (VCA_EVENT_NEW_FIGHT, iEventType=46)" },
                { "tiandy", 47, "肢体接触", "Body Touch", "vca", "肢体接触检测 (VCA_EVENT_BODY_TOUCH, iEventType=47)" },
                { "tiandy", 48, "人形检测", "Human Detect", "vca", "人形检测 (VCA_EVENT_HUMAN_DETECT, iEventType=48)" },
                { "tiandy", 49, "坝前堆积物检测", "Dam Alarm", "vca", "坝前堆积物检测 (VCA_EVENT_DAM_AMARM, iEventType=49)" },
                { "tiandy", 50, "站前拦网堆积物检测", "Net Alarm", "vca", "站前拦网堆积物检测 (VCA_EVENT_NET_AMARM, iEventType=50)" },
                { "tiandy", 51, "油田监控", "VCA PEPT", "vca", "油田监控 (VCA_EVENT_VCA_PEPT, iEventType=51)" },
                { "tiandy", 52, "水流速检测", "VCA Flow Speed", "vca", "水流速检测 (VCA_EVENT_VCA_FLOWSPEED, iEventType=52)" },
                { "tiandy", 53, "航标船", "Beacon Ship", "vca", "航标船检测 (VCA_EVENT_BEACON_SHIP, iEventType=53)" },
                { "tiandy", 54, "明厨亮灶", "Bright Kitchen", "vca", "明厨亮灶检测 (VCA_EVENT_BRIGHT_KITCHEN, iEventType=54)" },
                { "tiandy", 55, "滞留", "Stranded", "vca", "滞留检测 (VCA_EVENT_STRANDED, iEventType=55)" },
                { "tiandy", 56, "单人独处", "Single Alone", "vca", "单人独处检测 (VCA_EVENT_SINGLE_ALONE, iEventType=56)" },
                { "tiandy", 57, "隔窗递物", "Window Delivery", "vca", "隔窗递物检测 (VCA_EVENT_WINDOW_DELIVERY, iEventType=57)" },
                { "tiandy", 58, "吸烟", "Smoke", "vca", "吸烟检测 (VCA_EVENT_SMOKE, iEventType=58)" },
                { "tiandy", 59, "戴口罩", "Wear Mask", "vca", "戴口罩检测 (VCA_EVENT_WEAR_MASK, iEventType=59)" },
                { "tiandy", 60, "未戴口罩", "Not Wear Mask", "vca", "未戴口罩检测 (VCA_EVENT_NOT_WEAR_MASK, iEventType=60)" },
                { "tiandy", 61, "打电话", "Phone", "vca", "打电话检测 (VCA_EVENT_PHONE, iEventType=61)" },
                { "tiandy", 62, "环境温度检测", "Environment Temperature", "vca", "环境温度检测 (VCA_EVENT_EVETEMDETECT, iEventType=62)" },
                { "tiandy", 63, "人体温度检测", "Body Temperature", "vca", "人体温度检测 (VCA_EVENT_TEMDETECT, iEventType=63)" },
                { "tiandy", 64, "烟火检测", "Firework Detect", "vca", "烟火检测 (VCA_EVENT_FIREWORKDETECT, iEventType=64)" },
                { "tiandy", 65, "车牌黑名单", "Plate Number Blacklist", "its", "车牌黑名单检测 (VCA_EVENT_PLATENUMBER_BLACKLIST, iEventType=65)" },
                { "tiandy", 66, "智能侦测", "Smart Move", "vca", "智能侦测 (VCA_EVENT_SMART_MOVE, iEventType=66)" },
                { "tiandy", 67, "讯问超时", "Inquiry Timeout", "vca", "讯问超时检测 (VCA_EVENT_INUIRY_TIMEOUT, iEventType=67)" },
                { "tiandy", 68, "室内电动车检测", "Electric Vehicle", "vca", "室内电动车检测 (VCA_EVENT_ELECTRIC_VEHICLE, iEventType=68)" },
                { "tiandy", 69, "对象离席", "Leave Seat", "vca", "对象离席检测 (VCA_EVENT_LEAVE_SEAT, iEventType=69)" },
                { "tiandy", 70, "场景分类", "Scene Rec", "vca", "场景分类 (VCA_EVENT_SCENE_REC, iEventType=70)" },
                { "tiandy", 71, "违禁物品", "Contra Band", "vca", "违禁物品检测 (VCA_EVENT_CONTRA_BAND, iEventType=71)" },
                { "tiandy", 72, "未定时休息", "Bed Rest", "vca", "未定时休息检测 (VCA_EVENT_BED_REST, iEventType=72)" },
                { "tiandy", 73, "如厕无人看护", "Attended", "vca", "如厕无人看护检测 (VCA_EVENT_ATTENDED, iEventType=73)" },
                { "tiandy", 74, "谈话时未关闭房门", "Door", "vca", "谈话时未关闭房门检测 (VCA_EVENT_DOOR, iEventType=74)" },
                { "tiandy", 75, "长期举手/长期站立/长期下蹲", "Pose Rec", "vca", "长期举手/长期站立/长期下蹲检测 (VCA_EVENT_POSEREC, iEventType=75)" },
                { "tiandy", 76, "逆行", "Converse", "vca", "逆行检测 (VCA_EVENT_CONVERSE, iEventType=76)" },
                { "tiandy", 77, "智能审讯", "Court PII", "vca", "智能审讯 (VCA_EVENT_COURTPII, iEventType=77)" },
                { "tiandy", 78, "执法检人", "Court ELP", "vca", "执法检人 (VCA_EVENT_COURTELP, iEventType=78)" },
                { "tiandy", 79, "行为识别", "Behavior Rec", "vca", "行为识别（吸烟和/或打电话）(VCA_EVENT_BEHAVIREC, iEventType=79)" },
                { "tiandy", 80, "倾斜式人数统计", "Inclined Statis", "vca", "倾斜式人数统计 (VCA_EVENT_INCLINED_STATIS, iEventType=80)" },
                { "tiandy", 81, "垂直式人数统计", "Vertical Statis", "vca", "垂直式人数统计 (VCA_EVENT_VERTICAL_STATIS, iEventType=81)" },
                { "tiandy", 82, "人员聚集", "Person Gather", "vca", "(监管)人员聚集 (VCA_EVENT_PERSON_GATHER, iEventType=82)" },
                { "tiandy", 83, "人体温度正常", "Normal Body Temperature", "vca", "人体温度正常 (VCA_EVENT_NROMAL_BODY_TEMPERATURE, iEventType=83)" },
                { "tiandy", 84, "人员密度", "Person Density", "vca", "人员密度检测 (VCA_EVENT_PERSON_DENSITY, iEventType=84)" },
                { "tiandy", 85, "车辆密度", "Vehicle Density", "its", "车辆密度检测 (VCA_EVENT_VEHICLE_DENSITY, iEventType=85)" },
                { "tiandy", 86, "车辆拥堵", "Traffic Jam", "its", "车辆拥堵检测 (VCA_EVENT_TAFFIC_JAM, iEventType=86)" },
                { "tiandy", 87, "车辆滞留", "Vehicle Standed", "its", "车辆滞留检测 (VCA_EVENT_VEHICLE_STANDED, iEventType=87)" },
                { "tiandy", 88, "异常停车", "Abnormal Parking", "its", "异常停车检测 (VCA_EVENT_ABNORMAL_PARKING, iEventType=88)" },
                { "tiandy", 89, "交叉拥堵", "Cross Congestion", "its", "交叉拥堵检测 (VCA_EVENT_CROSS_CONGESTION, iEventType=89)" },
                { "tiandy", 90, "法官行为分析", "Judge Behavior Analyze", "vca", "法官行为分析 (VCA_EVENT_JUDGE_BEHAVIOR_ANALYZE, iEventType=90)" },
                { "tiandy", 91, "垂直检人", "Vertical Human Detection", "vca", "垂直检人 (VCA_EVENT_VERTICALHUMAN_DETECTION, iEventType=91)" },
                { "tiandy", 92, "高空抛物", "Aerial Projectile", "vca", "高空抛物检测 (VCA_EVENT_AERIAL_PROJECTILE, iEventType=92)" },
                { "tiandy", 93, "排污口监测", "Water Outfall", "vca", "排污口监测 (VCA_EVENT_WATER_OUTFALL, iEventType=93)" },
                { "tiandy", 94, "民警警服检测", "Police Uniform Detection", "vca", "民警警服检测 (VCA_EVENT_POLICE_UNIFORM_DETECTION, iEventType=94)" },
                { "tiandy", 95, "被监管人员识别服检测", "SP Dress Detection", "vca", "被监管人员识别服检测 (VCA_EVENT_SPDRESS_DETECTION, iEventType=95)" },
                { "tiandy", 96, "被监管人员队列检测", "SP Queue Detection", "vca", "被监管人员队列检测 (VCA_EVENT_SPQUEUE_DETECTION, iEventType=96)" },
                { "tiandy", 97, "车辆识别", "Vehicle Identify", "its", "车辆识别 (VCA_EVENT_VEHICLE_IDENTIFY, iEventType=97)" },
                { "tiandy", 98, "主动策略", "Active Strategy", "vca", "主动策略 (VCA_EVENT_ACTIVE_STRATEGY, iEventType=98)" },
                { "tiandy", 99, "占位", "Reserve", "vca", "占位 (VCA_EVENT_RESERVE_99, iEventType=99)" },
                { "tiandy", 100, "占位", "Reserve", "vca", "占位 (VCA_EVENT_RESERVE_100, iEventType=100)" },
                { "tiandy", 101, "占位", "Reserve", "vca", "占位 (VCA_EVENT_RESERVE_101, iEventType=101)" },
                { "tiandy", 102, "音频丢失报警", "Audio Lost Alarm", "vca", "音频丢失报警 (VCA_EVENT_AUDIO_LOST_ALARM, iEventType=102)" },
                { "tiandy", 103, "翻墙检测", "Climb Wall", "vca", "翻墙检测 (VCA_EVENT_CLIMB_WALL, iEventType=103)" },
                { "tiandy", 104, "课堂行为识别", "Classroom Behavior Recognition", "vca", "课堂行为识别 (VCA_EVENT_CLASSROOM_BEHAVIOR_RECOGNITION, iEventType=104)" },
                { "tiandy", 105, "水体颜色检测", "Water Color Detect", "vca", "水体颜色检测 (VCA_EVENT_WATER_COLOR_DETECT, iEventType=105)" },
                { "tiandy", 106, "睡觉异常检测", "Sleep Abnormal", "vca", "睡觉异常检测 (VCA_EVENT_SLEEP_ABNORMAL, iEventType=106)" },
                { "tiandy", 107, "玩手机检测", "Play Phones", "vca", "玩手机检测 (VCA_EVENT_PLAY_PHONES, iEventType=107)" },
                { "tiandy", 108, "后端结构化算法-人形", "Humanoid Detect", "vca", "后端结构化算法-人形 (VCA_EVENT_HUMANIOD_DETECT, iEventType=108)" },
                { "tiandy", 109, "后端结构化算法-机动车辆", "Vehicle Detect", "its", "后端结构化算法-机动车辆 (VCA_EVENT_VEHICLE_DETECT, iEventType=109)" },
                { "tiandy", 110, "后端结构化算法-非机动车", "Non Motor Detect", "its", "后端结构化算法-非机动车 (VCA_EVENT_NON_MOTOR_DETECT, iEventType=110)" },
                { "tiandy", 111, "车牌白名单", "Plate Whitelist", "its", "车牌白名单检测 (VCA_EVENT_PLATE_WHITELIST, iEventType=111)" },
                { "tiandy", 112, "人数检测", "Number Detection", "vca", "人数检测 (VCA_EVENT_NUMBER_DETECTION, iEventType=112)" },
                { "tiandy", 113, "图片分析检测", "Image Analysis Detection", "vca", "图片分析检测，涉及车辆，非机动车，人脸人体属性等 (VCA_EVENT_IMAGE_ANALYSIS_DETECTION, iEventType=113)" },
                { "tiandy", 114, "音频分析", "Audio Analysis", "vca", "音频分析 (VCA_EVENT_AUDIO_ANALYSIS, iEventType=114)" },
                { "tiandy", 115, "人员快速移动", "Person Fast Move", "vca", "人员快速移动检测 (VCA_EVENT_PERSON_FAST_MOVE, iEventType=115)" },
                { "tiandy", 116, "冲撞检测", "Collision Detection", "vca", "冲撞检测 (VCA_EVENT_COLLISION_DETECTION, iEventType=116)" },
                { "tiandy", 117, "押解异常", "Escort Anomaly", "vca", "押解异常检测 (VCA_EVENT_ESCORT_ANOMALY, iEventType=117)" },
                { "tiandy", 118, "用餐检测", "Meal Detection", "vca", "用餐检测 (VCA_EVENT_MEAL_DETECTION, iEventType=118)" },
                { "tiandy", 119, "警便服混穿", "Police Uniforms Mixed", "vca", "警便服混穿检测 (VCA_EVENT_POLICE_UNIFORMS_MIXED, iEventType=119)" },
                { "tiandy", 120, "非静止检测", "Non Static Detection", "vca", "非静止检测 (VCA_EVENT_NON_STATIC_DETECTION, iEventType=120)" },
                { "tiandy", 121, "未穿警便服", "Not Wear Police Uniforms", "vca", "未穿警便服检测 (VCA_EVENT_NOT_WEAR_POLICE_UNIFORMS, iEventType=121)" },
                { "tiandy", 122, "靶标检测", "Target Para", "vca", "靶标检测 (VCA_EVENT_TARGET_PARA, iEventType=122)" },
                { "tiandy", 123, "人形检测(智能监控)", "Human Detect Monitor", "vca", "人形检测(智能监控) (VCA_EVENT_HUMAN_DETECT_MONITOR, iEventType=123)" },
                { "tiandy", 124, "执纪检人", "Discipline Inspectors", "vca", "执纪检人 (VCA_EVENT_DISCIPLINE_INSPECTORS, iEventType=124)" },
                { "tiandy", 125, "智能动检", "Motion Detection", "vca", "智能动检 (VCA_EVENT_MOTION_DETECTION, iEventType=125)" },
                { "tiandy", 126, "煤气罐检测", "Gas Detection", "vca", "煤气罐检测 (VCA_EVENT_GAS_DETECTION, iEventType=126)" },
                { "tiandy", 127, "保密室试卷清点", "Test Paper Counting", "vca", "保密室试卷清点 (VCA_EVENT_TEST_PAPER_COUNTING, iEventType=127)" },
                { "tiandy", 128, "保密室试卷清点少于指定人数", "Test Paper Counting Numbers Lack", "vca", "保密室试卷清点少于指定人数 (VCA_EVENT_TEST_PAPER_COUNTING_NUMBERS_LACK, iEventType=128)" },
                { "tiandy", 129, "使用违规通讯工具", "Using Illegal Communication Tools", "vca", "使用违规通讯工具检测 (VCA_EVENT_USING_ILLEGAL_COMMUNICATION_TOOLS, iEventType=129)" },
                { "tiandy", 130, "携物外出", "Carry Things Out", "vca", "携物外出检测 (VCA_EVENT_CARRY_THINGS_OUT, iEventType=130)" },

                // ========== 扩展报警类型 (ALARM_TYPE_*) - 参考NetClientTypes.h ==========
                // 注意：这些报警类型与 iEventType 12-41 有重叠，但含义不同
                // ALARM_TYPE_* 是独立的报警类型，不是通过 NetClient_VCAGetAlarmInfo 获取的
                // 为了区分，我们使用 event_code 200+ 的范围来定义 ALARM_TYPE_* 报警类型
                // 这样避免与 iEventType 0-131 冲突
                { "tiandy", 200, "温度上限报警", "Temperature Upper Limit", "basic", "温度上限报警 (ALARM_TYPE_TEMPERATURE_UPPER_LIMIT, ALARM_TYPE_MIN+12)" },
                { "tiandy", 201, "温度下限报警", "Temperature Lower Limit", "basic", "温度下限报警 (ALARM_TYPE_TEMPERATURE_LOWER_LIMIT, ALARM_TYPE_MIN+13)" },
                { "tiandy", 202, "湿度上限报警", "Humidity Upper Limit", "basic", "湿度上限报警 (ALARM_TYPE_HUMIDITY_UPPER_LIMIT, ALARM_TYPE_MIN+14)" },
                { "tiandy", 203, "湿度下限报警", "Humidity Lower Limit", "basic", "湿度下限报警 (ALARM_TYPE_HUMIDITY_LOWER_LIMIT, ALARM_TYPE_MIN+15)" },
                { "tiandy", 204, "压力上限报警", "Pressure Upper Limit", "basic", "压力上限报警 (ALARM_TYPE_PRESSURE_UPPER_LIMIT, ALARM_TYPE_MIN+16)" },
                { "tiandy", 205, "压力下限报警", "Pressure Lower Limit", "basic", "压力下限报警 (ALARM_TYPE_PRESSURE_LOWER_LIMIT, ALARM_TYPE_MIN+17)" },
                { "tiandy", 207, "温湿度故障报警", "Temperature Humidity Fault", "basic", "温湿度故障报警 (ALARM_TYPE_TEMPERATURE_HUMIDITY_FAULT, ALARM_TYPE_MIN+19)" },
                { "tiandy", 208, "人脸识别报警", "Face Identification", "face", "人脸识别报警 (ALARM_TYPE_FACE_IDENT, ALARM_TYPE_MIN+20)" },
                { "tiandy", 209, "NVR智能分析", "NVR VCA", "vca", "NVR智能分析报警 (ALARM_TYPE_NVR_VCA, ALARM_TYPE_MIN+21)" },
                { "tiandy", 210, "恶意遮挡车牌", "Malicious Occlusion License Plate", "its", "恶意遮挡车牌报警 (ALARM_TYPE_MOLP, ALARM_TYPE_MIN+22)" },
                { "tiandy", 211, "降雨报警", "Rainfall", "basic", "降雨报警 (ALARM_TYPE_RAINFALL, ALARM_TYPE_MIN+23)" },
                { "tiandy", 212, "警戒水位报警", "Alert Water Level", "basic", "警戒水位报警 (ALARM_TYPE_ALERT_WATER_LEVEL, ALARM_TYPE_MIN+24)" },
                { "tiandy", 213, "传感器异常报警", "Sensor Abnormal", "basic", "传感器异常报警 (ALARM_TYPE_SENSOR_ABNORMAL, ALARM_TYPE_MIN+25)" },
                { "tiandy", 214, "ZF智能分析", "ZF VCA", "vca", "ZF智能分析报警 (ALARM_TYPE_ZF_VCA, ALARM_TYPE_MIN+26)" },
                { "tiandy", 215, "非法IP登录", "Illegal IP Login", "basic", "非法IP登录报警 (ALARM_TYPE_ILLEGAL_IP, ALARM_TYPE_MIN+27)" },
                { "tiandy", 216, "危险区域报警", "Dangerous Area", "basic", "导航船舶危险区域报警 (ALARM_TYPE_DANGEROUS_AREA, ALARM_TYPE_MIN+28)" },
                { "tiandy", 217, "电压上限报警", "Voltage High", "basic", "电压上限报警 (ALARM_TYPE_VOLTAGE_HIGH, ALARM_TYPE_MIN+29)" },
                { "tiandy", 218, "电压下限报警", "Voltage Low", "basic", "电压下限报警 (ALARM_TYPE_VOLTAGE_LOW, ALARM_TYPE_MIN+30)" },
                { "tiandy", 219, "黑体异常报警", "Black Body Detect", "basic", "黑体异常报警 (ALARM_TYPE_BLACK_BODY_DETECT, ALARM_TYPE_MIN+31)" },
                { "tiandy", 220, "警戒水位报警(下限)", "Alert Water Level Lower Limit", "basic", "警戒水位报警(下限) (ALARM_TYPE_ALERT_WATERLEVEL_LOWER_LIMIT, ALARM_TYPE_MIN+32)" },
                { "tiandy", 221, "预警水位报警(上限)", "Prealert Water Level Upper Limit", "basic", "预警水位报警(上限) (ALARM_TYPE_PREALERT_WATERLEVEL_UPPER_LIMIT, ALARM_TYPE_MIN+33)" },
                { "tiandy", 222, "预警水位报警(下限)", "Prealert Water Level Lower Limit", "basic", "预警水位报警(下限) (ALARM_TYPE_PREALERT_WATERLEVEL_LOWER_LIMIT, ALARM_TYPE_MIN+34)" },
                { "tiandy", 223, "主板拆卸报警", "Motherboard Disassemble", "basic", "主板拆卸报警 (ALARM_TYPE_MOTHERBOARD_DISASSEMBLE, ALARM_TYPE_MIN+35)" },
                { "tiandy", 224, "外接读卡器拆卸报警", "Out Cardreader Disassemble", "basic", "外接读卡器拆卸报警 (ALARM_TYPE_OUT_CARDREADER_DISASSEMBLE, ALARM_TYPE_MIN+36)" },
                { "tiandy", 225, "按钮报警", "Button", "basic", "按钮报警 (ALARM_TYPE_BUTTON, ALARM_TYPE_MIN+37)" },
                { "tiandy", 226, "车辆识别报警", "Vehicle Identification", "its", "车辆识别报警 (ALARM_TYPE_VEHICLE_IDENTIFICATION, ALARM_TYPE_MIN+38)" },
                { "tiandy", 227, "水利电子围栏超范围报警", "Water Electronic Overrange", "basic", "水利电子围栏超范围报警 (ALARM_TYPE_WATER_ELECTRONIC_OVERRANGE, ALARM_TYPE_MIN+39)" },
                { "tiandy", 228, "水利光谱数据异常报警", "Water Spectral Data Abnormal", "basic", "水利光谱数据异常报警 (ALARM_TYPE_WATER_SPECTRAL_DATA_ABNORMAL, ALARM_TYPE_MIN+40)" },
                { "tiandy", 229, "移动侦测之车形报警", "Motion Detection Car", "vca", "移动侦测之车形报警 (ALARM_TYPE_MOTION_DETECTION_CAR, ALARM_TYPE_MIN+41)" },
                
                // ========== 定制报警类型 ==========
                { "tiandy", 10000, "支付行为报警", "Payment Behavior", "vca", "支付行为报警 (ALARM_TYPE_PAYMENT_BEHAVIOR)" },
                { "tiandy", 10001, "电梯业务报警", "Elevator Service", "basic", "电梯业务报警 (ALARM_TYPE_ELEVATOR_SERVICE)" },
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
     * 根据品牌和事件代码获取事件类型的中文名称
     * @param connection 数据库连接
     * @param brand 品牌（如 "tiandy", "hikvision", "dahua"）
     * @param eventCode 事件代码（如 6, 3, 9）
     * @return 事件类型的中文名称，如果未找到则返回null
     */
    public static String getEventNameByCode(Connection connection, String brand, int eventCode) {
        String sql = "SELECT event_name FROM camera_event_types WHERE brand = ? AND event_code = ? LIMIT 1";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, brand.toLowerCase());
            pstmt.setInt(2, eventCode);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("event_name");
            }
        } catch (SQLException e) {
            logger.error("查询事件类型名称失败: brand={}, eventCode={}", brand, eventCode, e);
        }
        return null;
    }
    
    /**
     * 根据报警类型字符串（如 "Tiandy_Alarm_6"）获取事件类型的中文名称
     * @param connection 数据库连接
     * @param alarmType 报警类型字符串（格式：Brand_Alarm_Code）
     * @return 事件类型的中文名称，如果未找到则返回原始alarmType
     */
    public static String getEventNameByAlarmType(Connection connection, String alarmType) {
        if (alarmType == null || alarmType.isEmpty()) {
            return alarmType;
        }
        
        // 解析报警类型：Tiandy_Alarm_6 -> brand=tiandy, code=6
        String[] parts = alarmType.split("_");
        if (parts.length >= 3 && parts[1].equalsIgnoreCase("Alarm")) {
            String brand = parts[0].toLowerCase();
            try {
                int eventCode = Integer.parseInt(parts[2]);
                String eventName = getEventNameByCode(connection, brand, eventCode);
                if (eventName != null && !eventName.isEmpty()) {
                    return eventName;
                }
            } catch (NumberFormatException e) {
                // 忽略解析错误
            }
        }
        
        return alarmType; // 如果无法解析或未找到，返回原始值
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
