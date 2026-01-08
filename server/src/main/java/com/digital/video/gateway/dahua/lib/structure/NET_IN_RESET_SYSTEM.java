package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 恢复出厂设置入参
*/
public class NET_IN_RESET_SYSTEM extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    public int              dwSize;

    public NET_IN_RESET_SYSTEM() {
        this.dwSize = this.size();
    }
}

