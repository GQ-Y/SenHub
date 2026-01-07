package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 事件类型 DH_ALARM_TRAFFIC_NONMOTOR_WITHOUTSAFEHAT (非机动车未戴安全帽上报事件)对应的数据块描述信息
*/
public class NET_ALARM_TRAFFIC_NONMOTOR_WITHOUTSAFEHAT_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 通道号
    */
    public int              nChannel;
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
     * 事件ID
    */
    public int              nEventID;
    /**
     * 事件名称
    */
    public byte[]           szName = new byte[128];
    /**
     * 时间戳(单位是毫秒)
    */
    public double           dbPTS;
    /**
     * 事件组ID，同一辆车抓拍过程内GroupID相同
    */
    public int              nGroupID;
    /**
     * 一个事件组内的抓拍张数
    */
    public int              nCountInGroup;
    /**
     * 一个事件组内的抓拍序号
    */
    public int              nIndexInGroup;
    /**
     * 车道号
    */
    public int              nLane;
    /**
     * 非机动车信息,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.VA_OBJECT_NONMOTOR}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.VA_OBJECT_NONMOTOR stuNonMotor = new com.hikvision.nvr.dahua.lib.NetSDKLib.VA_OBJECT_NONMOTOR();
    /**
     * 公共信息,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.EVENT_COMM_INFO}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.EVENT_COMM_INFO stuCommInfo = new com.hikvision.nvr.dahua.lib.NetSDKLib.EVENT_COMM_INFO();
    /**
     * 交通车辆信息,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.DEV_EVENT_TRAFFIC_TRAFFICCAR_INFO}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.DEV_EVENT_TRAFFIC_TRAFFICCAR_INFO stuTrafficCar = new com.hikvision.nvr.dahua.lib.NetSDKLib.DEV_EVENT_TRAFFIC_TRAFFICCAR_INFO();
    /**
     * 抓拍序号，如3-2-1/0，1表示抓拍正常结束，0表示抓拍异常结束
    */
    public int              nSequence;
    /**
     * 保留字节
    */
    public byte[]           szReserved = new byte[1020];

    public NET_ALARM_TRAFFIC_NONMOTOR_WITHOUTSAFEHAT_INFO() {
    }
}

