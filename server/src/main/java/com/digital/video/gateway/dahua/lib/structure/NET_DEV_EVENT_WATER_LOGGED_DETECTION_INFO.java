package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 事件类型 EVENT_IVS_WATER_LOGGED_DETECTION (内涝积水事件)对应的数据块描述信息
*/
public class NET_DEV_EVENT_WATER_LOGGED_DETECTION_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 通道号
    */
    public int              nChannelID;
    /**
     * 0:脉冲,1:开始, 2:停止
    */
    public int              nAction;
    /**
     * 扩展协议字段,参见结构体定义 {@link com.netsdk.lib.structure.NET_EVENT_INFO_EXTEND}
    */
    public NET_EVENT_INFO_EXTEND stuEventInfoEx = new NET_EVENT_INFO_EXTEND();
    /**
     * 事件发生的时间,参见结构体定义 {@link com.netsdk.lib.com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TIME_EX}
    */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TIME_EX stuUTC = new com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TIME_EX();
    /**
     * 事件组ID，同一辆车抓拍过程内GroupID相同。
    */
    public int              nGroupID;
    /**
     * CountInGroup一个事件组内应有的抓拍张数
    */
    public int              nCountInGroup;
    /**
     * 一个事件组内的抓拍序号，从1开始
    */
    public int              nIndexInGroup;
    /**
     * 事件名称
    */
    public byte[]           szName = new byte[128];
    /**
     * 智能事件所属大类
    */
    public byte[]           szClass = new byte[16];
    /**
     * 相对事件时间戳,单位毫秒
    */
    public double           dbPTS;
    /**
     * 检测到的物体信息,参见结构体定义 {@link com.netsdk.lib.com.digital.video.gateway.dahua.lib.NetSDKLib.NET_MSG_OBJECT}
    */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.NET_MSG_OBJECT[] stuObjects = new com.digital.video.gateway.dahua.lib.NetSDKLib.NET_MSG_OBJECT[100];
    /**
     * 检测到的物体个数
    */
    public int              nObjectNum;
    /**
     * 事件编号
    */
    public int              nEventID;
    /**
     * 检测区个数
    */
    public int              nDetectRegionNum;
    /**
     * 检测区,参见结构体定义 {@link com.netsdk.lib.structure.NET_POINT_EX}
    */
    public NET_POINT_EX[]   stuDetectRegion = new NET_POINT_EX[20];
    /**
     * 事件触发的预置点号，从1开始
    */
    public int              nPresetID;
    /**
     * 配置时预设的报警区域占比等级
    */
    public int              nAlarmLevel;
    /**
     * 区域占比值，取值范围0-100
    */
    public int              nAlarmRatio;
    /**
     * 全景广角图在上传图片数据中的图片序号
    */
    public int              nIndexInDataInSceneImage;
    /**
     * 预留字节
    */
    public byte[]           szReserved = new byte[1008];

    public NET_DEV_EVENT_WATER_LOGGED_DETECTION_INFO() {
        for(int i = 0; i < stuObjects.length; i++){
            stuObjects[i] = new com.digital.video.gateway.dahua.lib.NetSDKLib.NET_MSG_OBJECT();
        }
        for(int i = 0; i < stuDetectRegion.length; i++){
            stuDetectRegion[i] = new NET_POINT_EX();
        }
    }
}

