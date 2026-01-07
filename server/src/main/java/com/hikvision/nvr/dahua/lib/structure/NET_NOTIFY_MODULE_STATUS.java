package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 工作状态信息
*/
public class NET_NOTIFY_MODULE_STATUS extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 工作状态,"Normal":正常工作;"Abnormal":不正常工作
    */
    public byte[]           szState = new byte[32];
    /**
     * 预留字段
    */
    public byte[]           szResvered = new byte[1024];

    public NET_NOTIFY_MODULE_STATUS() {
    }
}

