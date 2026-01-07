package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;

public class CFG_TIME_SECTION_ARRAY_10 extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
    /**
     * 参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.CFG_TIME_SECTION}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.CFG_TIME_SECTION[] obj_10 = new com.hikvision.nvr.dahua.lib.NetSDKLib.CFG_TIME_SECTION[10];

    public CFG_TIME_SECTION_ARRAY_10() {
        for(int i = 0; i < obj_10.length; i++){
            obj_10[i] = new com.hikvision.nvr.dahua.lib.NetSDKLib.CFG_TIME_SECTION();
        }
    }
}

