package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * DDNS配置结构体
*/
public class NETDEV_MULTI_DDNS_CFG extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    public int              dwSize;
    public int              dwDdnsServerNum;
    /**
     * 参见结构体定义 {@link com.netsdk.lib.structure.NET_DDNS_SERVER_CFG}
    */
    public NET_DDNS_SERVER_CFG[] struDdnsServer = new NET_DDNS_SERVER_CFG[10];

    public NETDEV_MULTI_DDNS_CFG() {
        this.dwSize = this.size();
        for(int i = 0; i < struDdnsServer.length; i++){
            struDdnsServer[i] = new NET_DDNS_SERVER_CFG();
        }
    }
}

