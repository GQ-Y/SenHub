package com.hikvision.nvr.dahua.lib.structure;


import com.hikvision.nvr.dahua.lib.NetSDKLib;

/**
 * @author 260611
 * @description 设备列表
 * @date 2022/04/20 11:31:57
 */
public class NET_THINGS_DEVLIST_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
    /**
     * "1","2"等
     */
    public byte[]           szDevID = new byte[128];
    /**
     * "001","002"等
     */
    public byte[]           szDevClass = new byte[128];
    /**
     * 预留字节
     */
    public byte[]           szReserved = new byte[1024];
}

