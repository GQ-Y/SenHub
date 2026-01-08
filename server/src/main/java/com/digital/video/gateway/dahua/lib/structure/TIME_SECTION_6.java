package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * @author 251823
 * @description 6个NET_TSECT时间段结构体
 * @date 2022/10/14 13:53:01
 */
public class TIME_SECTION_6 extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
    public com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TSECT[] timeSection = new com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TSECT[com.digital.video.gateway.dahua.lib.NetSDKLib.NET_N_REC_TSECT];
}

