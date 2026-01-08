package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;

/**
 * 疫区经历信息
 */
public class NET_EPIDEMIC_AREA_EXPERIENCE extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
    /**
     * 疫区地址
     */
    public byte[]           szAddress = new byte[128];
    /**
     * 在疫区时间
     */
    public NET_TIME         stuTime;
    /**
     * 预留字段
     */
    public byte[]           byReserved = new byte[256];
}

