package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
import com.sun.jna.Pointer;
/**
 * 事件类型 EVENT_IVS_TRAFFIC_TURNLEFTAFTERSTRAIGHT (左转未礼让直行事件)对应的数据块描述信息
*/
public class DEV_EVENT_TURNLEFTAFTERSTRAIGHT_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
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
     * 保留字节对齐
    */
    public byte[]           Reserved = new byte[4];
    /**
     * 时间戳(单位是毫秒)
    */
    public double           PTS;
    /**
     * 事件发生的时间,参见结构体定义 {@link com.netsdk.lib.com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TIME_EX}
    */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TIME_EX UTC = new com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TIME_EX();
    /**
     * 事件ID
    */
    public int              nEventID;
    /**
     * 事件对应文件信息,参见结构体定义 {@link com.netsdk.lib.com.digital.video.gateway.dahua.lib.NetSDKLib.NET_EVENT_FILE_INFO}
    */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.NET_EVENT_FILE_INFO stuFileInfo = new com.digital.video.gateway.dahua.lib.NetSDKLib.NET_EVENT_FILE_INFO();
    /**
     * 车牌信息,参见结构体定义 {@link com.netsdk.lib.com.digital.video.gateway.dahua.lib.NetSDKLib.NET_MSG_OBJECT}
    */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.NET_MSG_OBJECT stuObject = new com.digital.video.gateway.dahua.lib.NetSDKLib.NET_MSG_OBJECT();
    /**
     * 车身信息,参见结构体定义 {@link com.netsdk.lib.com.digital.video.gateway.dahua.lib.NetSDKLib.NET_MSG_OBJECT}
    */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.NET_MSG_OBJECT stuVehicle = new com.digital.video.gateway.dahua.lib.NetSDKLib.NET_MSG_OBJECT();
    /**
     * 车辆信息,参见结构体定义 {@link com.netsdk.lib.com.digital.video.gateway.dahua.lib.NetSDKLib.DEV_EVENT_TRAFFIC_TRAFFICCAR_INFO}
    */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.DEV_EVENT_TRAFFIC_TRAFFICCAR_INFO stTrafficCar = new com.digital.video.gateway.dahua.lib.NetSDKLib.DEV_EVENT_TRAFFIC_TRAFFICCAR_INFO();
    /**
     * 对应车道号
    */
    public int              nLane;
    /**
     * 表示抓拍序号,如3,2,1,1表示抓拍结束,0表示异常结束
    */
    public int              nSequence;
    /**
     * 事件动作,0表示脉冲事件,1表示持续性事件开始,2表示持续性事件结束;
    */
    public byte             byEventAction;
    /**
     * 图片的序号, 同一时间内(精确到秒)可能有多张图片, 从0开始
    */
    public byte             byImageIndex;
    public byte[]           byReserved1 = new byte[2];
    /**
     * 抓图标志(按位),具体见NET_RESERVED_COMMON
    */
    public int              dwSnapFlagMask;
    /**
     * 对应图片的分辨率,参见结构体定义 {@link com.netsdk.lib.com.digital.video.gateway.dahua.lib.NetSDKLib.NET_RESOLUTION_INFO}
    */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.NET_RESOLUTION_INFO stuResolution = new com.digital.video.gateway.dahua.lib.NetSDKLib.NET_RESOLUTION_INFO();
    /**
     * GPS信息,参见结构体定义 {@link com.netsdk.lib.com.digital.video.gateway.dahua.lib.NetSDKLib.NET_GPS_INFO}
    */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.NET_GPS_INFO stuGPSInfo = new com.digital.video.gateway.dahua.lib.NetSDKLib.NET_GPS_INFO();
    /**
     * 全景图,参见结构体定义 {@link com.netsdk.lib.com.digital.video.gateway.dahua.lib.NetSDKLib.SCENE_IMAGE_INFO_EX}
    */
    public Pointer          pstuSceneImage;
    /**
     * 保留字节
    */
    public byte[]           byReserved = new byte[984-com.digital.video.gateway.dahua.lib.NetSDKLib.POINTERSIZE];
    /**
     * 公共信息,参见结构体定义 {@link com.netsdk.lib.com.digital.video.gateway.dahua.lib.NetSDKLib.EVENT_COMM_INFO}
    */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.EVENT_COMM_INFO stCommInfo = new com.digital.video.gateway.dahua.lib.NetSDKLib.EVENT_COMM_INFO();
    /**
     * 扩展协议字段,参见结构体定义 {@link com.netsdk.lib.structure.NET_EVENT_INFO_EXTEND}
    */
    public NET_EVENT_INFO_EXTEND stuEventInfoEx = new NET_EVENT_INFO_EXTEND();

    public DEV_EVENT_TURNLEFTAFTERSTRAIGHT_INFO() {
    }
}

