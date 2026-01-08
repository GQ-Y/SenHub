package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;

public class CFG_TIME_SECTION_ARRAY_10 extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
    /**
     * 参见结构体定义 {@link com.netsdk.lib.com.digital.video.gateway.dahua.lib.NetSDKLib.CFG_TIME_SECTION}
    */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.CFG_TIME_SECTION[] obj_10 = new com.digital.video.gateway.dahua.lib.NetSDKLib.CFG_TIME_SECTION[10];

    public CFG_TIME_SECTION_ARRAY_10() {
        for(int i = 0; i < obj_10.length; i++){
            obj_10[i] = new com.digital.video.gateway.dahua.lib.NetSDKLib.CFG_TIME_SECTION();
        }
    }
}

