package com.hikvision.nvr.dahua.lib.structure;

import com.hikvision.nvr.dahua.lib.NetSDKLib;

/**
 * @author 251823
 * @description  网络模式字符串对应字节数组
 * @date 2021/09/17
 */
public class SupportedModeByteArr extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
	/**
	 *  网络模式字符串对应字节数组
	 */
    public byte[]           supportedModeByteArr = new byte[com.hikvision.nvr.dahua.lib.NetSDKLib.NET_MAX_MODE_LEN];
}

