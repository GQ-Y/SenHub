package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * CLIENT_MonitorWallGetScene接口输出参数(获取电视墙场景)
*/
public class NET_OUT_MONITORWALL_GET_SCENE extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    public int              dwSize;
    /**
     * 当前预案名称, 可以为空
    */
    public byte[]           szName = new byte[128];
    /**
     * 电视墙场景,参见结构体定义 {@link com.netsdk.lib.structure.NET_MONITORWALL_SCENE}
    */
    public NET_MONITORWALL_SCENE stuScene = new NET_MONITORWALL_SCENE();

    public NET_OUT_MONITORWALL_GET_SCENE() {
        this.dwSize = this.size();
    }
}

