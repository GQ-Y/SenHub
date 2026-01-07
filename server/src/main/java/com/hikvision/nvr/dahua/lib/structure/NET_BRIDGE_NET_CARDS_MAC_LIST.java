package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 网卡内部网卡信息
*/
public class NET_BRIDGE_NET_CARDS_MAC_LIST extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 网卡名称
    */
    public byte[]           szNetCardName = new byte[32];
    /**
     * 网卡Mac
    */
    public byte[]           szNetCardMac = new byte[18];
    /**
     * 预留字节
    */
    public byte[]           szReserved = new byte[14];

    public NET_BRIDGE_NET_CARDS_MAC_LIST() {
    }
}

