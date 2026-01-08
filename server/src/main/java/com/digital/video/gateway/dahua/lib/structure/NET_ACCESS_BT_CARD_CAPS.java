package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 蓝牙相关能力
*/
public class NET_ACCESS_BT_CARD_CAPS extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 每次最大插入量
    */
    public int              nMaxInsertRate;
    /**
     * 最大存储的卡数量
    */
    public int              nMaxCards;
    /**
     * 保留字节
    */
    public byte[]           szReserved = new byte[512];

    public NET_ACCESS_BT_CARD_CAPS() {
    }
}

