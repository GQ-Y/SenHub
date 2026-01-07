package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 事件类型 DH_ALARM_ASGFOLLOWING (闸机尾随报警事件) 对应的数据块描述信息
*/
public class ALARM_ASGFOLLOWING_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 0:脉冲 1:开始 2:停止
    */
    public int              nAction;
    /**
     * 通道号
    */
    public int              nChannelID;
    /**
     * 事件发生的时间,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME_EX}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME_EX UTC = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME_EX();
    /**
     * 事件公共扩展字段结构体,参见结构体定义 {@link com.netsdk.lib.structure.NET_EVENT_INFO_EXTEND}
    */
    public NET_EVENT_INFO_EXTEND stuEventInfoEx = new NET_EVENT_INFO_EXTEND();
    /**
     * 预留字节
    */
    public byte[]           byReserved = new byte[1020];

    public ALARM_ASGFOLLOWING_INFO() {
    }
}

