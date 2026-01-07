package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * CLIENT_MonitorWallSetScene接口输出参数(设置电视墙场景)
*/
public class NET_OUT_MONITORWALL_SET_SCENE extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    public int              dwSize;

    public NET_OUT_MONITORWALL_SET_SCENE() {
        this.dwSize = this.size();
    }
}

