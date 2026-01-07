package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 设备异常报警状态灯色控制
*/
public class NET_ABNORMAL_ALARM_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 视频遮挡状态灯色,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_PARKINGSPACELIGHT_INFO}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_PARKINGSPACELIGHT_INFO stuVideoBlind = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_PARKINGSPACELIGHT_INFO();
    /**
     * 烟雾火焰状态灯色,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_PARKINGSPACELIGHT_INFO}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_PARKINGSPACELIGHT_INFO stuSmokeFire = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_PARKINGSPACELIGHT_INFO();
    /**
     * 保留字节
    */
    public byte[]           szReserved = new byte[256];

    public NET_ABNORMAL_ALARM_INFO() {
    }
}

