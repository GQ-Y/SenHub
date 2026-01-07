package com.hikvision.nvr.dahua.lib.structure;

import com.hikvision.nvr.dahua.lib.NetSDKLib;

/**
 * @author 47081
 * @version 1.0
 * @description 楼层号,不要超过999
 * @date 2021/2/8
 */
public class NET_FLOORS_EX extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
    public byte[]           szFloorEx = new byte[8];
}

