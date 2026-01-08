package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 事件类型EVENT_IVS_SEATING_RATE_DETECTION(就座率检测事件)对应的规则配置
*/
public class CFG_SEATING_RATE_DETECTION_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 规则名称,不同规则不能重名
    */
    public byte[]           szRuleName = new byte[128];
    /**
     * 规则使能
    */
    public int              bRuleEnable;
    /**
     * 相应物体类型个数
    */
    public int              nObjectTypeNum;
    /**
     * 相应物体类型列表
    */
    public BYTE_ARRAY_128[] szObjectTypes = new BYTE_ARRAY_128[16];
    /**
     * 报警联动,参见结构体定义 {@link com.netsdk.lib.com.digital.video.gateway.dahua.lib.NetSDKLib.CFG_ALARM_MSG_HANDLE}
    */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.CFG_ALARM_MSG_HANDLE stuEventHandler = new com.digital.video.gateway.dahua.lib.NetSDKLib.CFG_ALARM_MSG_HANDLE();
    /**
     * 事件响应时间段,参见结构体定义 {@link com.netsdk.lib.structure.CFG_TIME_SECTION}
    */
    public CFG_TIME_SECTION_ARRAY_10[] stuTimeSection = new CFG_TIME_SECTION_ARRAY_10[7];
    /**
     * 云台预置点编号  0~65535
    */
    public int              nPtzPresetId;
    /**
     * 最短持续时间，单位是秒,低于该时间不报警，默认0
    */
    public int              nMinDuration;
    /**
     * 重复报警间隔,为0不重复报警,单位：秒，默认0
    */
    public int              nReportInterval;
    /**
     * 灵敏度，取值1-10，数值越大代表灵敏度越高
    */
    public int              nSensitivity;
    /**
     * 检测模式: 0-大于阈值报警 ,1-小于阈值报警 ,2-不等于阈值报警 ,3-等于阈值报警,4-人数变化报警（该模式下只要灵敏度参数）
    */
    public int              nDetectType;
    /**
     * 最小检测高度,单位cm，默认0
    */
    public int              nMinHeight;
    /**
     * 最大检测高度,单位cm，默认0
    */
    public int              nMaxHeight;
    /**
     * 周期上报时间间隔,1-15分钟可配，默认5分钟
    */
    public int              nPeriodicReportInterval;
    /**
     * 报警阈值百分比
    */
    public int              nThreshold;
    /**
     * 就座率的检测时长设置（范围1~30分钟，默认3分钟）
    */
    public int              nDetectionDuration;
    /**
     * 智慧录播子场景信息：1-教师场景 2-学生场景，无此配置不区分场景
    */
    public int              nRecordedSceneType;
    /**
     * 总座位数
    */
    public int              nSeatCount;
    /**
     * 检测区顶点数
    */
    public int              nDetectRegionNum;
    /**
     * 检测区,参见结构体定义 {@link com.netsdk.lib.structure.NET_CFG_POLYGON}
    */
    public NET_CFG_POLYGON[] stuDetectRegion = new NET_CFG_POLYGON[20];
    /**
     * 排除域：检测区域中需要排除的区域，一个检测区域对应1个排除区域 坐标点归一化到0~8192，默认为NULL,参见结构体定义 {@link com.netsdk.lib.structure.NET_CFG_POLYGON}
    */
    public NET_CFG_POLYGON[] stuExcludeRegion = new NET_CFG_POLYGON[20];
    /**
     * 排除域顶点数
    */
    public int              nExcludeRegionNum;
    /**
     * 预留字节
    */
    public byte[]           szReserved = new byte[1020];

    public CFG_SEATING_RATE_DETECTION_INFO() {
        for(int i = 0; i < szObjectTypes.length; i++){
            szObjectTypes[i] = new BYTE_ARRAY_128();
        }
        for(int i = 0; i < stuTimeSection.length; i++){
            stuTimeSection[i] = new CFG_TIME_SECTION_ARRAY_10();
        }
        for(int i = 0; i < stuDetectRegion.length; i++){
            stuDetectRegion[i] = new NET_CFG_POLYGON();
        }
        for(int i = 0; i < stuExcludeRegion.length; i++){
            stuExcludeRegion[i] = new NET_CFG_POLYGON();
        }
    }
}

