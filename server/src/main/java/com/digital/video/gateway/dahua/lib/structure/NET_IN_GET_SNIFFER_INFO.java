package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
import com.sun.jna.Pointer;
/**
 * CLIENT_GetSnifferInfo 接口输入参数
*/
public class NET_IN_GET_SNIFFER_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    public int              dwSize;
    public Pointer          pszNetInterface;

    public NET_IN_GET_SNIFFER_INFO() {
        this.dwSize = this.size();
    }
}

