package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
import com.sun.jna.Pointer;
/**
 * 通知导出包裹信息
*/
public class NET_NOTIFY_PACKAGE_MANUAL_EXPORT extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 包裹列表,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_XRAY_PKG_INFO}
    */
    public Pointer          pstuList;
    /**
     * 包裹列表个数
    */
    public int              nListNum;
    /**
     * 预留字段
    */
    public byte[]           szResvered = new byte[1020];

    public NET_NOTIFY_PACKAGE_MANUAL_EXPORT() {
    }
}

