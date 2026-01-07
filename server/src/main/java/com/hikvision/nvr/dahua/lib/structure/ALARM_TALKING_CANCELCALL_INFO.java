package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 设备呼叫中取消呼叫事件(对应事件 DH_ALARM_TALKING_CANCELCALL)
*/
public class ALARM_TALKING_CANCELCALL_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 事件发生的时间,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME_EX}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME_EX stuTime = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME_EX();
    /**
     * 呼叫ID
    */
    public byte[]           szCallID = new byte[32];

    public ALARM_TALKING_CANCELCALL_INFO() {
    }
}

