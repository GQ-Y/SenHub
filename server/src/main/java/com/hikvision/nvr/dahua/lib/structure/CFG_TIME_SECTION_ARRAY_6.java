package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;

public class CFG_TIME_SECTION_ARRAY_6 extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
    /**
     * 参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.CFG_TIME_SECTION}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.CFG_TIME_SECTION[] obj_6 = new com.hikvision.nvr.dahua.lib.NetSDKLib.CFG_TIME_SECTION[6];

    public CFG_TIME_SECTION_ARRAY_6() {
        for(int i = 0; i < obj_6.length; i++){
            obj_6[i] = new com.hikvision.nvr.dahua.lib.NetSDKLib.CFG_TIME_SECTION();
        }
    }
}

