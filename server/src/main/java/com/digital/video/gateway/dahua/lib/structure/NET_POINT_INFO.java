package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;


/**
 *
 * 
 * @author ： 260611
 * @since ： Created in 2021/10/19 19:35
 */
public class NET_POINT_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
    /**
     *  主相机标定点
     */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.DH_POINT stuMasterPoint = new com.digital.video.gateway.dahua.lib.NetSDKLib.DH_POINT();
    /**
     *  从相机(球机)标定点
     */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.DH_POINT stuSlavePoint = new com.digital.video.gateway.dahua.lib.NetSDKLib.DH_POINT();
    /**
     *  保留字段
     */
    public byte             byReserved[] = new byte[256];
}

