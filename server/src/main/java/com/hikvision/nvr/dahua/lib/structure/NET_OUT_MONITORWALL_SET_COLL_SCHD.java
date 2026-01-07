package com.hikvision.nvr.dahua.lib.structure;

import com.hikvision.nvr.dahua.lib.NetSDKLib;

/**
 * 设置预案时间表 出参 {@link NetSDKLib#CLIENT_OperateMonitorWall}
 *
 * @author ： 47040
 * @since ： Created in 2020/10/19 10:42
 */
public class NET_OUT_MONITORWALL_SET_COLL_SCHD extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
    /**
     * 结构体大小
     */
    public int              dwSize;

    public NET_OUT_MONITORWALL_SET_COLL_SCHD() {
        dwSize = this.size();
    }
}

