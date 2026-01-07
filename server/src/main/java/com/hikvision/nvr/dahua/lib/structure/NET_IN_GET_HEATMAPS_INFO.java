package com.hikvision.nvr.dahua.lib.structure;

import com.hikvision.nvr.dahua.lib.NetSDKLib;

/**
 * @author 251589
 * @version 1.0
 * @description {@link NetSDKLib#CLIENT_GetHeatMapsDirectly}的输入参数
 * @date 2020/11/10
 **/


public class NET_IN_GET_HEATMAPS_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
    /**
     *     DWORD dwSize;
     *     int  nChannel;
     */
    public int              dwSize;
    public int              nChannel;                             // 通道号, 通道号要与订阅时一致, -1除外

    public NET_IN_GET_HEATMAPS_INFO() {
        this.dwSize = this.size();
    }
}

