package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 设备呼叫中取消呼叫事件(对应事件 DH_ALARM_TALKING_CANCELCALL)
*/
public class ALARM_TALKING_CANCELCALL_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 事件发生的时间,参见结构体定义 {@link com.netsdk.lib.com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TIME_EX}
    */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TIME_EX stuTime = new com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TIME_EX();
    /**
     * 呼叫ID
    */
    public byte[]           szCallID = new byte[32];

    public ALARM_TALKING_CANCELCALL_INFO() {
    }
}

