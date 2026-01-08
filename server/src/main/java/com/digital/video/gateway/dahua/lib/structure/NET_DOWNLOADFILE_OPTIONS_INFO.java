package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;

/**
 * 文件方式下载选项
 *
 * @author ： 47040
 * @since ： Created in 2020/12/28 16:09
 */
public class NET_DOWNLOADFILE_OPTIONS_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
    /**
     * 通道号
     */
    public int              nChannel;
    /**
     * 预留字段
     */
    public byte[]           byReserved = new byte[508];
}

