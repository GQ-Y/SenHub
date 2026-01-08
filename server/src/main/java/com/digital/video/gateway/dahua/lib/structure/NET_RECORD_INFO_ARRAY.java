package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;

/**
 * @author ： 47040
 * @since ： Created in 2020/9/28 18:55
 */
public class NET_RECORD_INFO_ARRAY extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
    public NET_RECORD_INFO[] stuRecordInfo_2 = new NET_RECORD_INFO[16];

    public NET_RECORD_INFO_ARRAY() {
        for (int i = 0; i < stuRecordInfo_2.length; i++) {
            stuRecordInfo_2[i] = new NET_RECORD_INFO();
        }
    }
}

