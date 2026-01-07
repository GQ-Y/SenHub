package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 事件类型 EVENT_IVS_OBJECT_QUANTITY_DETECTION (目标类型和数量检测报警事件)对应的数据块描述信息
*/
public class NET_DEV_EVENT_OBJECT_QUANTITY_DETECTION_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
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
     * 事件ID
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
     * 相对事件时间戳,(单位是毫秒)
    */
    public double           dbPTS;
    /**
     * 检测目标的物体信息,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_MSG_OBJECT}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_MSG_OBJECT[] stuObjects = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_MSG_OBJECT[128];
    /**
     * 实际有效目标检测信息个数
    */
    public int              nObjectsRealNum;
    /**
     * 检测区域数组有效个数
    */
    public int              nDetectRegionRealNum;
    /**
     * 检测区域
    */
    public INT_ARRAY_2[]    nDetectRegion = new INT_ARRAY_2[20];
    /**
     * 预留字节
    */
    public byte[]           szReserved = new byte[1024];

    public NET_DEV_EVENT_OBJECT_QUANTITY_DETECTION_INFO() {
        for(int i = 0; i < stuObjects.length; i++){
            stuObjects[i] = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_MSG_OBJECT();
        }
    }
}

