package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;

/**
 * @author 47081
 * @version 1.0
 * @description 车牌
 * @date 2021/2/22
 */
public class PlateNumber extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
    public byte[]           plateNumber = new byte[32];
}

