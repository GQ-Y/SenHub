package com.hikvision.nvr.dahua.lib.structure;

import com.hikvision.nvr.dahua.lib.NetSDKLib;

/**
 * @author 47081
 * @version 1.0
 * @description 用户的开门时间段
 * @date 2021/2/8
 */
public class USER_TIME_SECTION extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
    public byte[]           userTimeSections = new byte[20];
}

