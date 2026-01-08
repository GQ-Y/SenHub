package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;

import static com.digital.video.gateway.dahua.lib.NetSDKLib.NET_COMMON_STRING_128;

/**
 * 电视墙名称 拆分自{@link NET_IN_MONITORWALL_GET_ENABLE}
 *
 * @author ： 47040
 * @since ： Created in 2020/10/19 11:08
 */
public class NET_MONITORWALL_NAME extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
    /**
     * 电视墙名称
     */
    public byte[]           szName = new byte[NET_COMMON_STRING_128];
}

