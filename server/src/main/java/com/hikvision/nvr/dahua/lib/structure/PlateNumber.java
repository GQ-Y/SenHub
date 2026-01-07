package com.hikvision.nvr.dahua.lib.structure;

import com.hikvision.nvr.dahua.lib.NetSDKLib;

/**
 * @author 47081
 * @version 1.0
 * @description 车牌
 * @date 2021/2/22
 */
public class PlateNumber extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
    public byte[]           plateNumber = new byte[32];
}

