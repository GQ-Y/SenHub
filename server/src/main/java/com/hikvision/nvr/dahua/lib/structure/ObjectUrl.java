package com.hikvision.nvr.dahua.lib.structure;

import com.hikvision.nvr.dahua.lib.NetSDKLib;
import static com.hikvision.nvr.dahua.lib.NetSDKLib.MAX_PATH;

/**
 * @author 47081
 * @version 1.0
 * @description
 * @date 2021/2/22
 */
public class ObjectUrl extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
    public byte[]           objectUrl = new byte[MAX_PATH];
}

