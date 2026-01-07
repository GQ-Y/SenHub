package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 目标事件列表
*/
public class NET_OBJECT_EVENTS_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 记录唯一编号
    */
    public int              nRecNo;
    /**
     * 通道号，从0开始
    */
    public int              nChannel;
    /**
     * 目标轨迹ID
    */
    public int              nObjectID;
    /**
     * 目标类型
    */
    public int              nObjectType;
    /**
     * 目标轨迹所关联的智能大类值
    */
    public byte[]           szSchemaID = new byte[32];
    /**
     * 目标事件小图
    */
    public byte[]           szObjectUrl = new byte[256];
    /**
     * 本地系统utc时间（0时区），单位：秒
    */
    public long             nUTCSystemTime;
    /**
     * 事件触发utc时间（0时区），单位：秒
    */
    public long             nUTCEventTime;
    /**
     * 物体进入画面时间,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME stuEnterTimeRealUTC = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME();
    /**
     * 物体离开画面时间,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME stuLeaveTimeRealUTC = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME();
    /**
     * 关联记录编号
    */
    public int              nLinkID;
    /**
     * 保留字节
    */
    public byte[]           szReserved = new byte[1020];

    public NET_OBJECT_EVENTS_INFO() {
    }
}

