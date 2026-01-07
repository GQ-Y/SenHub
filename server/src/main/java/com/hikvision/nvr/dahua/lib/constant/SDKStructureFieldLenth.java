package com.hikvision.nvr.dahua.lib.constant;

/**
 * SDK结构体字段长度常量
 * 用于大华SDK结构体定义
 */
public class SDKStructureFieldLenth {
    // 通用常量
    public static final int MAX_PATH = 260;
    public static final int MAX_NAME_LEN = 64;
    public static final int MAX_ADDRESS_LEN = 128;
    
    // 通道相关
    public static final int MAX_CHANNEL_ID_LEN = 64;
    public static final int MAX_PREVIEW_CHANNEL_NUM = 32;
    public static final int MAX_COURSE_LOGIC_CHANNEL = 32;
    
    // 字符串长度
    public static final int CFG_COMMON_STRING_32 = 32;
    public static final int CFG_COMMON_STRING_64 = 64;
    public static final int NET_COMMON_STRING_64 = 64;
    public static final int NET_COMMON_STRING_128 = 128;
    public static final int MAX_COMMON_STRING_64 = 64;
    
    // 设备相关
    public static final int NET_DEVICE_NAME_LEN = 64;
    public static final int NET_MAX_POLYGON_NUM = 16;
    public static final int NET_MAX_MODE_NUMBER = 32;
    public static final int NET_MAX_MONITORWALL_NUM = 32;
    public static final int NET_MAX_COLLECTION_NUM = 32;
    public static final int NET_MAX_WINDOWS_NUMBER = 16;
    
    // 检测区域
    public static final int NET_MAX_DETECT_REGION_NUM = 8;
    public static final int SDK_MAX_DETECT_REGION_NUM = 8;
    public static final int DH_MAX_DETECT_REGION_NUM = 8;
    public static final int MAX_DETECT_LINE_NUM = 4;
    public static final int MAX_MOTION_ROW = 22;
    public static final int MAX_MOTION_COL = 22;
    public static final int MAX_MOTION_WINDOW = 22 * 22;
    
    // 对象相关
    public static final int HDBJ_MAX_OBJECTS_NUM = 16;
    public static final int MAX_INSIDEOBJECT_NUM = 16;
    public static final int MAX_OBJECT_LIST_SIZE = 16;
    public static final int MAX_MASKTYPE_COUNT = 8;
    public static final int MAX_MOSAICTYPE_COUNT = 8;
    
    // 时间相关
    public static final int NET_TSCHE_SEC_NUM = 6;
    public static final int WEEK_DAY_NUM = 7;
    public static final int MAX_REC_TSECT = 8;
    public static final int MAX_REC_TSECT_EX = 8;
    
    // SIP相关
    public static final int MAX_SIP_SERVER_NUM = 8;
    public static final int MAX_SIP_SVR_ID_LEN = 64;
    public static final int MAX_SIP_DOMAIN_LEN = 64;
    public static final int MAX_SIP_SVR_IP_LEN = 64;
    public static final int MAX_SIP_SERVER_DEVICE_ID_LEN = 64;
    public static final int MAX_REG_PASSWORD_LEN = 64;
    public static final int MAX_CIVIL_CODE_LEN = 20;
    public static final int MAX_INTERVIDEO_ID_LEN = 64;
    
    // 事件相关
    public static final int MAX_EVENT_NAME_LEN = 64;
    public static final int NET_EVENT_NAME_LEN = 64;
    public static final int NET_MAX_TRACK_LINE_NUM = 4;
    
    // 其他
    public static final int CFG_MAX_LOWER_MATRIX_OUTPUT = 16;
    public static final int CFG_MAX_CAPTURE_SIZE_NUM = 32;
    public static final int CFG_MAX_AUDIO_ENCODE_TYPE = 16;
    public static final int CFG_MAX_COMPRESSION_TYPES_NUM = 16;
    public static final int MAX_PROTOCOL_NAME_LEN = 64;
    public static final int MAX_SCENICSPOT_POINTS_NUM = 16;
    public static final int CUSTOM_TITLE_LEN = 64;
    public static final int NET_MAX_PERSON_NAME_LEN = 64;
    public static final int NET_MAX_PERSON_ID_LEN = 32;
    public static final int NET_MAX_PERSON_IMAGE_NUM = 8;
    public static final int NET_MAX_PROVINCE_NAME_LEN = 32;
    public static final int DH_MAX_PERSON_INFO_LEN = 256;
    public static final int NET_MAX_RAID_DEVICE_NAME = 64;
    public static final int NET_MAX_FAN_NUM = 16;
    public static final int NET_MAX_POWER_NUM = 16;
    public static final int NET_MAX_BATTERY_NUM = 16;
    public static final int NET_MAX_TEMPERATURE_NUM = 16;
    public static final int NET_MAX_CPU_NUM = 16;
    public static final int NET_GATEWAY_MAX_SIM_NUM = 4;
    public static final int POINTERSIZE = 8;
    public static final int MAX_CALIBRATE_POINT_NUM = 16;
    
    // 编码相关
    public static final int CFG_MAX_VIDEO_CHANNEL_NUM = 64;
    public static final int CFG_MAX_PREVIEW_MODE_SPLIT_TYPE_NUM = 16;
    
    // 其他缺失的常量（使用合理的默认值）
    public static final int NET_MAX_ALARM_INFO_NUM = 16;
    public static final int NET_MAX_COMPOSE_CHANNEL_NUM = 16;
    public static final int NET_MAX_DOWNLOAD_FILE_NUM = 16;
    public static final int NET_MAX_2DCODE_LEN = 128;
    public static final int NET_MAX_LIGHTING_CONTROL_NUM = 16;
    public static final int NET_MAX_HIGH_TOSS_DETECT_NUM = 16;
    public static final int NET_MAX_PTZ_AUTOMOVE_NUM = 16;
    public static final int NET_MAX_PRIVACY_MASKING_NUM = 16;
    public static final int NET_MAX_SHOP_WINDOW_POST_NUM = 16;
    
    // 字符串常量
    public static final int SDK_COMMON_STRING_512 = 512;
    
    // 密码相关
    public static final int MAX_PASSWORD_LEN = 64;
    
    // 配置相关
    public static final int MAX_CONFIG_NUM = 16;
}
