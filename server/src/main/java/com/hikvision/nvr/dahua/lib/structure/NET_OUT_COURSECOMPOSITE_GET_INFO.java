package com.hikvision.nvr.dahua.lib.structure;

import com.hikvision.nvr.dahua.lib.NetSDKLib;

/**
 * 获取组合通道信息出参 {@link NetSDKLib#CLIENT_OperateCourseCompositeChannel}
 * @author ： 47040
 * @since ： Created in 2020/9/28 20:05
 */
public class NET_OUT_COURSECOMPOSITE_GET_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
    /**
     * 结构体大小
     */
    public int              dwSize;
    /**
     * 组合通道信息
     */
    public NET_COMPOSITECHANNEL_INFO stuChannelInfo;

    public NET_OUT_COURSECOMPOSITE_GET_INFO() {
        dwSize = this.size();
    }
}

