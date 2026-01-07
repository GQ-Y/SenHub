package com.hikvision.nvr.dahua.lib.structure;

import com.hikvision.nvr.dahua.lib.NetSDKLib;

/**
 * 获取录像状态 入参 {@link NetSDKLib#CLIENT_GetRecordState}
 *
 * @author ： 47040
 * @since ： Created in 2020/10/13 20:26
 */
public class NET_IN_GET_RECORD_STATE extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
    /**
     * 结构体大小
     */
    public int              dwSize;
    /**
     * 通道号
     */
    public int              nChannel;

    public NET_IN_GET_RECORD_STATE() {
        this.dwSize = size();
    }
}

