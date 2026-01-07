package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * NET_MANUAL_TEST_AREA_INFO 区域信息
*/
public class NET_MANUAL_TEST_AREA_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 区域名称
    */
    public byte[]           szName = new byte[128];
    /**
     * 区域号，从0开始
    */
    public int              nIndex;
    /**
     * 预留字节
    */
    public byte[]           szReserved = new byte[28];

    public NET_MANUAL_TEST_AREA_INFO() {
    }
}

