package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
import com.sun.jna.Pointer;
/**
 * 事件类型 EVENT_IVS_TRAFFIC_ASSISTANT_WITHOUT_SAFEBELT (交通副驾驶未系安全带事件)对应的数据块描述信息
*/
public class DEV_EVENT_TRAFFIC_ASSISTANT_WITHOUT_SAFEBELT extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 通道号
    */
    public int              nChannelID;
    /**
     * 事件名称
    */
    public byte[]           szName = new byte[128];
    /**
     * TriggerType:触发类型,0车检器,1雷达,2视频
    */
    public int              nTriggerType;
    /**
     * 时间戳(单位是毫秒)
    */
    public int              PTS;
    /**
     * 事件发生的时间,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME_EX}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME_EX UTC = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME_EX();
    /**
     * 事件ID
    */
    public int              nEventID;
    /**
     * 表示抓拍序号,如3,2,1,1表示抓拍结束,0表示异常结束
    */
    public int              nSequence;
    /**
     * 事件动作,0表示脉冲事件,1表示持续性事件开始,2表示持续性事件结束;    BYTE                    byReserved1[2];
    */
    public byte             byEventAction;
    /**
     * 图片的序号, 同一时间内(精确到秒)可能有多张图片, 从0开始
    */
    public byte             byImageIndex;
    public byte[]           byReserved1 = new byte[2];
    /**
     * 事件对应文件信息,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_EVENT_FILE_INFO}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_EVENT_FILE_INFO stuFileInfo = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_EVENT_FILE_INFO();
    /**
     * 对应车道号
    */
    public int              nLane;
    /**
     * 底层产生的触发抓拍帧标记
    */
    public int              nMark;
    /**
     * 视频分析帧序号
    */
    public int              nFrameSequence;
    /**
     * 视频分析的数据源地址
    */
    public int              nSource;
    /**
     * 车牌信息,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_MSG_OBJECT}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_MSG_OBJECT stuObject = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_MSG_OBJECT();
    /**
     * 车身信息,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_MSG_OBJECT}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_MSG_OBJECT stuVehicle = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_MSG_OBJECT();
    /**
     * 交通车辆信息,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.DEV_EVENT_TRAFFIC_TRAFFICCAR_INFO}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.DEV_EVENT_TRAFFIC_TRAFFICCAR_INFO stuTrafficCar = new com.hikvision.nvr.dahua.lib.NetSDKLib.DEV_EVENT_TRAFFIC_TRAFFICCAR_INFO();
    /**
     * 车辆实际速度,Km/h
    */
    public int              nSpeed;
    /**
     * 抓图标志(按位),具体见NET_RESERVED_COMMON
    */
    public int              dwSnapFlagMask;
    /**
     * 公共信息,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.EVENT_COMM_INFO}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.EVENT_COMM_INFO stCommInfo = new com.hikvision.nvr.dahua.lib.NetSDKLib.EVENT_COMM_INFO();
    /**
     * 全景图,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.SCENE_IMAGE_INFO_EX}
    */
    public Pointer          pstuSceneImage;
    /**
     * 车身信息扩展,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_MSG_OBJECT_EX2}
    */
    public Pointer          pstuVehicleEx2;
    /**
     * 预留字节
    */
    public byte[]           szReserved = new byte[1024-2*com.hikvision.nvr.dahua.lib.NetSDKLib.POINTERSIZE];

    public DEV_EVENT_TRAFFIC_ASSISTANT_WITHOUT_SAFEBELT() {
    }
}

