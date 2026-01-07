package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
import com.sun.jna.Pointer;
/**
 * 事件类型 EVENT_IVS_TRAFFIC_OVER_TAKE_DURING_MEETING 对应的数据块描述信息
*/
public class NET_DEV_EVENT_TRAFFIC_OVER_TAKE_DURING_MEETING_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 通道号
    */
    public int              nChannelID;
    /**
     * 0：脉冲，1：开始，2：停止
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
     * 事件编号,用来唯一标志一个事件
    */
    public int              nEventID;
    /**
     * 事件名称
    */
    public byte[]           szName = new byte[128];
    /**
     * 相对事件时间戳,单位毫秒
    */
    public double           dbPTS;
    /**
     * GroupID事件组ID,同一物体抓拍过程内GroupID相同
    */
    public int              nGroupID;
    /**
     * CountInGroup一个事件组内的抓拍张数
    */
    public int              nCountInGroup;
    /**
     * IndexInGroup一个事件组内的抓拍序号
    */
    public int              nIndexInGroup;
    /**
     * 车道号，从0开始
    */
    public int              nLane;
    /**
     * 车牌信息,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_MSG_OBJECT_EX2}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_MSG_OBJECT_EX2 stuObject = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_MSG_OBJECT_EX2();
    /**
     * 车辆信息,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_MSG_OBJECT_EX2}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_MSG_OBJECT_EX2 stuVehicle = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_MSG_OBJECT_EX2();
    /**
     * 非机动车信息,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.VA_OBJECT_NONMOTOR}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.VA_OBJECT_NONMOTOR stuNonMotor = new com.hikvision.nvr.dahua.lib.NetSDKLib.VA_OBJECT_NONMOTOR();
    /**
     * 交通车辆的数据库记录,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.DEV_EVENT_TRAFFIC_TRAFFICCAR_INFO}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.DEV_EVENT_TRAFFIC_TRAFFICCAR_INFO stuTrafficCar = new com.hikvision.nvr.dahua.lib.NetSDKLib.DEV_EVENT_TRAFFIC_TRAFFICCAR_INFO();
    /**
     * 公共信息,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.EVENT_COMM_INFO}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.EVENT_COMM_INFO stuCommInfo = new com.hikvision.nvr.dahua.lib.NetSDKLib.EVENT_COMM_INFO();
    /**
     * 图片信息数组，内存由NetSDK申请释放,参见结构体定义 {@link com.netsdk.lib.structure.NET_IMAGE_INFO_EX3}
    */
    public Pointer          pstuImageInfo;
    /**
     * 图片信息有效个数
    */
    public int              nImageInfoNum;
    /**
     * 抓拍序号，如3-2-1/0，1表示抓拍正常结束，0表示抓拍异常结束
    */
    public int              nSequence;
    /**
     * 预留字节
    */
    public byte[]           szReserved = new byte[1024];

    public NET_DEV_EVENT_TRAFFIC_OVER_TAKE_DURING_MEETING_INFO() {
    }
}

