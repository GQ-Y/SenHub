package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 事件类型 EVENT_IVS_TRAFFIC_QUEUE_OVERFLOW (排队溢出事件)对应的数据块描述信息
*/
public class NET_DEV_EVENT_TRAFFIC_QUEUE_OVERFLOW_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
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
     * 事件名称
    */
    public byte[]           szName = new byte[128];
    /**
     * GroupID事件组ID，同一物体抓拍过程内GroupID相同
    */
    public int              nGroupID;
    /**
     * CountInGroup一个事件组内的抓拍张数
    */
    public int              nCountInGroup;
    /**
     * IndexInGroup一个事件组内的抓拍序号，从1开始
    */
    public int              nIndexInGroup;
    /**
     * 对应车道号
    */
    public int              nLane;
    /**
     * 视频分析帧序号
    */
    public int              nFrameSequence;
    /**
     * 抓拍序号, 1表示抓拍正常结束, 0表示抓拍异常结束
    */
    public int              nSequence;
    /**
     * 排队长度
    */
    public float            fQueueLength;
    /**
     * 事件ID
    */
    public int              nEventID;
    /**
     * 相对事件时间戳,(单位是毫秒)
    */
    public double           dbPTS;
    /**
     * 事件发生的时间,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME_EX}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME_EX stuUTC = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME_EX();
    /**
     * 是否有车辆信息
    */
    public int              bVehicleInfo;
    /**
     * 是否有车牌信息
    */
    public int              bObjectInfo;
    /**
     * 是否有非机动车信息
    */
    public int              bNonMotorInfo;
    /**
     * 车辆信息,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_MSG_OBJECT}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_MSG_OBJECT stuVehicle = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_MSG_OBJECT();
    /**
     * 车牌信息,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_MSG_OBJECT}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_MSG_OBJECT stuObject = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_MSG_OBJECT();
    /**
     * 非机动车信息,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.VA_OBJECT_NONMOTOR}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.VA_OBJECT_NONMOTOR stuNonMotor = new com.hikvision.nvr.dahua.lib.NetSDKLib.VA_OBJECT_NONMOTOR();
    /**
     * 扩展协议字段,参见结构体定义 {@link com.netsdk.lib.structure.NET_EVENT_INFO_EXTEND}
    */
    public NET_EVENT_INFO_EXTEND stuEventInfoEx = new NET_EVENT_INFO_EXTEND();
    /**
     * 溢流状态 0:未知1:即将溢出 2:溢出 3:溢停
    */
    public int              nOverflowStatus;
    /**
     * 预留字节
    */
    public byte[]           szReserved = new byte[1020];

    public NET_DEV_EVENT_TRAFFIC_QUEUE_OVERFLOW_INFO() {
    }
}

