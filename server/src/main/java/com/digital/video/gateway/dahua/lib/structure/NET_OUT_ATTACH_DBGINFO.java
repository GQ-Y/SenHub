package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 订阅日志回调出参
*/
public class NET_OUT_ATTACH_DBGINFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    public int              dwSize;

    public NET_OUT_ATTACH_DBGINFO() {
        this.dwSize = this.size();
    }
}

