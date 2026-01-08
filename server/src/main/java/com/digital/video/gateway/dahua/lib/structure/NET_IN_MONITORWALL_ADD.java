package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 添加电视墙输入参数
*/
public class NET_IN_MONITORWALL_ADD extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    public int              dwSize;
    /**
     * 电视墙信息,参见结构体定义 {@link com.netsdk.lib.com.digital.video.gateway.dahua.lib.NetSDKLib.NET_MONITORWALL}
    */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.NET_MONITORWALL stuMonitorWall = new com.digital.video.gateway.dahua.lib.NetSDKLib.NET_MONITORWALL();

    public NET_IN_MONITORWALL_ADD() {
        this.dwSize = this.size();
    }
}

