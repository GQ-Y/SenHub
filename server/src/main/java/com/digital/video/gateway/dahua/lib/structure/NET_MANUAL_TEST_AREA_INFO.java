package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * NET_MANUAL_TEST_AREA_INFO 区域信息
*/
public class NET_MANUAL_TEST_AREA_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
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

