package com.hikvision.nvr.dahua.lib.structure;

import com.hikvision.nvr.dahua.lib.NetSDKLib;

/**
 * @author 251823
 * @description CLIENT_GetDeviceAllInfo 输入结构体
 * @date 2021/01/20
 */
public class NET_IN_GET_DEVICE_AII_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
	/**
     * 赋值为结构体大小
     */
    public int              dwSize;

    public NET_IN_GET_DEVICE_AII_INFO(){
        this.dwSize=size();
    }
}

