package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 事件类型 EVENT_IVS_TRAFFIC_SUSPICIOUS_CAR(嫌疑车辆事件)对应的数据块描述信息
*/
public class NET_DEV_EVENT_TRAFFIC_SUSPICIOUS_CAR_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
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
     * 对齐字节
    */
    public byte[]           szReservedUTC = new byte[4];
    /**
     * 检测到的车辆信息,参见结构体定义 {@link com.netsdk.lib.com.digital.video.gateway.dahua.lib.NetSDKLib.NET_MSG_OBJECT}
    */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.NET_MSG_OBJECT stuVehicle = new com.digital.video.gateway.dahua.lib.NetSDKLib.NET_MSG_OBJECT();
    /**
     * 禁止名单信息,参见结构体定义 {@link com.netsdk.lib.com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TRAFFIC_LIST_RECORD}
    */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TRAFFIC_LIST_RECORD stuCarInfo = new com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TRAFFIC_LIST_RECORD();
    /**
     * 交通事件公共信息,参见结构体定义 {@link com.netsdk.lib.com.digital.video.gateway.dahua.lib.NetSDKLib.EVENT_COMM_INFO}
    */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.EVENT_COMM_INFO stuCommInfo = new com.digital.video.gateway.dahua.lib.NetSDKLib.EVENT_COMM_INFO();
    /**
     * 保留字节
    */
    public byte[]           szReserved = new byte[1024];

    public NET_DEV_EVENT_TRAFFIC_SUSPICIOUS_CAR_INFO() {
    }
}

