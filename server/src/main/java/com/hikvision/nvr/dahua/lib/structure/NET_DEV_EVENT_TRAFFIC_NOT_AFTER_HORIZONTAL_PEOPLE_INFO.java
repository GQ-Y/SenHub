package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
import com.sun.jna.Pointer;
/**
 * 事件类型 EVENT_IVS_TRAFFIC_NOT_AFTER_HORIZONTAL_PEOPLE (不礼让行人事件)对应的数据块描述信息
*/
public class NET_DEV_EVENT_TRAFFIC_NOT_AFTER_HORIZONTAL_PEOPLE_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
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
     * 事件发生的时间,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME_EX}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME_EX stuUTC = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME_EX();
    /**
     * 事件编号，用来唯一标志一个事件
    */
    public int              nEventID;
    /**
     * 事件名称
    */
    public byte[]           szName = new byte[128];
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
     * 表示对应车道号
    */
    public int              nLane;
    /**
     * 相对事件时间戳,单位毫秒
    */
    public double           dbPTS;
    /**
     * 车辆信息,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_MSG_OBJECT}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_MSG_OBJECT stuVehicle = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_MSG_OBJECT();
    /**
     * 车牌信息,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_MSG_OBJECT}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_MSG_OBJECT stuObject = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_MSG_OBJECT();
    /**
     * 表示交通车辆的数据库记录,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.DEV_EVENT_TRAFFIC_TRAFFICCAR_INFO}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.DEV_EVENT_TRAFFIC_TRAFFICCAR_INFO stuTrafficCar = new com.hikvision.nvr.dahua.lib.NetSDKLib.DEV_EVENT_TRAFFIC_TRAFFICCAR_INFO();
    /**
     * 公共信息,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.EVENT_COMM_INFO}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.EVENT_COMM_INFO stuCommInfo = new com.hikvision.nvr.dahua.lib.NetSDKLib.EVENT_COMM_INFO();
    /**
     * 抓拍序号，如/0，1表示抓拍正常结束，0表示抓拍异常结束
    */
    public int              nSequence;
    /**
     * 全景图,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.SCENE_IMAGE_INFO_EX}
    */
    public Pointer          pstuSceneImage;
    /**
     * 预留字节
    */
    public byte[]           szReserved = new byte[1020-com.hikvision.nvr.dahua.lib.NetSDKLib.POINTERSIZE];

    public NET_DEV_EVENT_TRAFFIC_NOT_AFTER_HORIZONTAL_PEOPLE_INFO() {
    }
}

