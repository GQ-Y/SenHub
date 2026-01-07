package com.hikvision.nvr.dahua.lib.structure;

import com.hikvision.nvr.dahua.lib.NetSDKLib;

/**
 * @author 251823
 * @description CLIENT_QueryDevInfo NET_QUERY_DEV_IO_STATS 类型接口输入参数
 * @date 2021/07/09
 */
public class NET_IN_DEV_IO_STATS extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
	/**
     * 结构体大小
     */
    public int              dwSize;

    public NET_IN_DEV_IO_STATS(){
        this.dwSize=size();
    }
}

