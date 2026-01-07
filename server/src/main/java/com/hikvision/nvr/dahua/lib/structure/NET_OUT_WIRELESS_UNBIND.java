package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * CLIENT_WirelessUnbind 接口出参
*/
public class NET_OUT_WIRELESS_UNBIND extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 此结构体大小,必须赋值
    */
    public int              dwSize;

    public NET_OUT_WIRELESS_UNBIND() {
        this.dwSize = this.size();
    }
}

