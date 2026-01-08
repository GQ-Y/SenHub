package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;

/**
 * @author 47081
 * @version 1.0
 * @description 热度图订阅接口出参
 * @date 2020/9/21
 */
public class NET_OUT_ATTACH_VIDEOSTAT_HEATMAP extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
    public int              dwSize;

    public NET_OUT_ATTACH_VIDEOSTAT_HEATMAP() {
        this.dwSize = size();
    }
}

