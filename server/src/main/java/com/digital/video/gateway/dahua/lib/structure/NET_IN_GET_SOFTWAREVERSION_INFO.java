package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * CLIENT_GetSoftwareVersion 入参
*/
public class NET_IN_GET_SOFTWAREVERSION_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 结构体大小
    */
    public int              dwSize;

    public NET_IN_GET_SOFTWAREVERSION_INFO() {
        this.dwSize = this.size();
    }
}

