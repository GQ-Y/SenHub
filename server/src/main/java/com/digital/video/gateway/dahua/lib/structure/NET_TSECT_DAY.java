package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;

import static com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TSCHE_SEC_NUM;

/**
 * 拆分自{@link MONITORWALL_COLLECTION_SCHEDULE}
 *
 * @author ： 47040
 * @since ： Created in 2020/10/19 9:43
 */
public class NET_TSECT_DAY extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
    /**
     * 时间段结构
     */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TSECT[] stuSchedule = new com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TSECT[NET_TSCHE_SEC_NUM];

    public NET_TSECT_DAY() {
        for (int i = 0; i < stuSchedule.length; i++) {
            stuSchedule[i] = new com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TSECT();
        }
    }
}

