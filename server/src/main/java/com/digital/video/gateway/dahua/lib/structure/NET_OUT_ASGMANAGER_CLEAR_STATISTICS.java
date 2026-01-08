package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * CLIENT_ASGManagerClearStatistics 出参
*/
public class NET_OUT_ASGMANAGER_CLEAR_STATISTICS extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 结构体大小
    */
    public int              dwSize;

    public NET_OUT_ASGMANAGER_CLEAR_STATISTICS() {
        this.dwSize = this.size();
    }
}

