package com.hikvision.nvr.dahua.lib.structure;


import com.hikvision.nvr.dahua.lib.NetSDKLib;

/**
 * @author 291189
 * @description CLIENT_GetRadiometryCurrentHotColdSpotInfo 入参
 * @origin autoTool
 * @date 2023/08/07 13:51:18
 */
public class NET_IN_RADIOMETRY_CURRENTHOTCOLDSPOT_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
    public int              dwSize;
    /**
     * 通道号：只有热成像通道有效
     */
    public int              nChannel;

    public NET_IN_RADIOMETRY_CURRENTHOTCOLDSPOT_INFO() {
        this.dwSize = this.size();
    }
}

