package com.digital.video.gateway.dahua.lib.structure;


import com.digital.video.gateway.dahua.lib.NetSDKLib;

/**
 * @author 260611
 * @description int+Array值
 * @date 2022/04/20 11:31:59
 */
public class NET_PROPERTIES_INTARRAY_VALUE extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
    /**
     * Value：1,2,3
     */
    public int              nValue;
    /**
     * 预留字节
     */
    public byte[]           szReserved = new byte[32];
}

