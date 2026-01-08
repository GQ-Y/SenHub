package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;

import static com.digital.video.gateway.dahua.lib.NetSDKLib.MAX_PREVIEW_CHANNEL_NUM;

/**
 * 获取默认真实通道号出参，对应接口 {@link NetSDKLib#CLIENT_GetDefaultRealChannel}
 *
 * @author ： 47040
 * @since ： Created in 2020/9/28 10:11
 */
public class NET_OUT_GET_DEFAULT_REAL_CHANNEL extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
    /**
     * 结构体大小
     */
    public int              dwSize;
    /**
     * 通道数量
     */
    public int              nChannelNum;
    /**
     * 通道号
     */
    public int[]            nChannel = new int[MAX_PREVIEW_CHANNEL_NUM];

    public NET_OUT_GET_DEFAULT_REAL_CHANNEL(){
        dwSize = this.size();
    }
}

