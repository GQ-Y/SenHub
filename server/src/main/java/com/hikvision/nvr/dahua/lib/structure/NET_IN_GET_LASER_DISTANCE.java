package com.hikvision.nvr.dahua.lib.structure;


import com.hikvision.nvr.dahua.lib.NetSDKLib;

/**
 * @author 260611
 * @description CLIENT_GetLaserDistance 接口入参
 * @date 2023/04/21 09:16:51
 */
public class NET_IN_GET_LASER_DISTANCE extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
    /**
     * 结构体大小
     */
    public int              dwSize;
    /**
     * 通道号,从0开始
     */
    public int              nChannel;

    public NET_IN_GET_LASER_DISTANCE() {
        this.dwSize = this.size();
    }
}

