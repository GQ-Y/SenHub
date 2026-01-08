package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;

/**
 * @author 251823
 * @description 拥挤人群列表
 * @date 2022/01/07
 */
public class NET_GETSUMMARY_CROWD_LIST_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
    /**
     *  拥挤人群中心点坐标
     */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.NET_POINT stuCenter;
    /**
     *  半径
     */
    public int              nRadius;
    /**
     *  保留字节
     */
    public byte[]           szReserved = new byte[1024];
}

