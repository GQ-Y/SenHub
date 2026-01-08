package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 事件类型 EVENT_IVS_SEATING_RATE_DETECTION (就座率检测事件)对应的数据块描述信息
*/
public class NET_DEV_EVENT_SEATING_RATE_DETECTION_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
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
     * 事件编号
    */
    public int              nEventID;
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
     * 事件触发的预置点号, 从1开始, 0表示预置点未知
    */
    public int              nPresetID;
    /**
     * 总座位数
    */
    public int              nSeatCount;
    /**
     * 检测到的总人数
    */
    public int              nHumanCount;
    /**
     * 预留字节
    */
    public byte[]           szReserved = new byte[1020];

    public NET_DEV_EVENT_SEATING_RATE_DETECTION_INFO() {
    }
}

