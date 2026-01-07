package com.hikvision.nvr.dahua.lib.structure;

import static com.hikvision.nvr.dahua.lib.constant.SDKStructureFieldLenth.CFG_COMMON_STRING_32;/**
 * @author 47081
 * @descriptio
 * @date 2020/11/9
 * @version 1.0
 */

import com.hikvision.nvr.dahua.lib.NetSDKLib;

// import static com.hikvision.nvr.dahua.lib.constant.SDKStructureFieldLenth.CFG_COMMON_STRING_32;

/**
 * @author 47081
 * @version 1.0
 * @description
 * @date 2020/11/9
 */
public class Auxs extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
    public byte[]           auxs = new byte[CFG_COMMON_STRING_32];
}

