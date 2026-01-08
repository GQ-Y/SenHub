package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;
import static com.digital.video.gateway.dahua.lib.NetSDKLib.MAX_PATH;

/**
 * @author 47081
 * @version 1.0
 * @description
 * @date 2021/2/22
 */
public class ObjectUrl extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
    public byte[]           objectUrl = new byte[MAX_PATH];
}

