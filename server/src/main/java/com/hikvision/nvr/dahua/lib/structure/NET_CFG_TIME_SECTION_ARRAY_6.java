package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;

public class NET_CFG_TIME_SECTION_ARRAY_6 extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
    /**
     * 参见结构体定义 {@link com.netsdk.lib.structure.NET_CFG_TIME_SECTION}
    */
    public NET_CFG_TIME_SECTION[] obj_6 = new NET_CFG_TIME_SECTION[6];

    public NET_CFG_TIME_SECTION_ARRAY_6() {
        for(int i = 0; i < obj_6.length; i++){
            obj_6[i] = new NET_CFG_TIME_SECTION();
        }
    }
}

