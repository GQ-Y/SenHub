package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 道路拥堵规则
*/
public class CFG_CONGESTION_SUPPORTED_RULES extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 是否支持报表
    */
    public int              bSupportLocalDataStore;
    /**
     * 预留字节
    */
    public byte[]           szReserved = new byte[1020];

    public CFG_CONGESTION_SUPPORTED_RULES() {
    }
}

