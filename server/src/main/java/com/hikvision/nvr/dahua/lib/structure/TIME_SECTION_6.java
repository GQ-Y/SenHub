package com.hikvision.nvr.dahua.lib.structure;

import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * @author 251823
 * @description 6个NET_TSECT时间段结构体
 * @date 2022/10/14 13:53:01
 */
public class TIME_SECTION_6 extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TSECT[] timeSection = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TSECT[com.hikvision.nvr.dahua.lib.NetSDKLib.NET_N_REC_TSECT];
}

