package com.hikvision.nvr.dahua.lib.structure;


import com.hikvision.nvr.dahua.lib.NetSDKLib;

/**
 * @author 260611
 * @description 物模型标识
 * @date 2022/04/20 11:31:59
 */
public class NET_PROPERTIES_NAME extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
    /**
     * "*","BJPZ_DDBJLDMS"、"SBJCXX_CPLX"、"SBJCXX_SBLX"等
     */
    public byte[]           szPropertiesName = new byte[64];
    /**
     * 预留字节
     */
    public byte[]           szReserved = new byte[1024];
}

