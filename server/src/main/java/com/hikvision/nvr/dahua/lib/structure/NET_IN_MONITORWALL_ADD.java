package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 添加电视墙输入参数
*/
public class NET_IN_MONITORWALL_ADD extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    public int              dwSize;
    /**
     * 电视墙信息,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_MONITORWALL}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_MONITORWALL stuMonitorWall = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_MONITORWALL();

    public NET_IN_MONITORWALL_ADD() {
        this.dwSize = this.size();
    }
}

