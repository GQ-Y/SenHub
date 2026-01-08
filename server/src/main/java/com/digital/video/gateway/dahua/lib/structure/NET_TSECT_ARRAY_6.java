package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;

public class NET_TSECT_ARRAY_6 extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
    /**
     * 参见结构体定义 {@link com.netsdk.lib.com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TSECT}
    */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TSECT[] obj_6 = new com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TSECT[6];

    public NET_TSECT_ARRAY_6() {
        for(int i = 0; i < obj_6.length; i++){
            obj_6[i] = new com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TSECT();
        }
    }
}

