package com.hikvision.nvr.dahua.lib.structure;

import com.hikvision.nvr.dahua.lib.NetSDKLib;

/**
 * @author 251823
 * @description CLIENT_GetRtscGlobalParam 接口输入参数
 * @date 2021/09/28
 */
public class NET_IN_GET_GLOBAL_PARAMETER extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
	 /**
     *  结构体大小
     */
    public int              dwSize;

    public NET_IN_GET_GLOBAL_PARAMETER(){
        this.dwSize = this.size();
    }
}

