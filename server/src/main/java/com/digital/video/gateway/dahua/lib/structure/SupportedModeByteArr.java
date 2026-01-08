package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;

/**
 * @author 251823
 * @description  网络模式字符串对应字节数组
 * @date 2021/09/17
 */
public class SupportedModeByteArr extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
	/**
	 *  网络模式字符串对应字节数组
	 */
    public byte[]           supportedModeByteArr = new byte[com.digital.video.gateway.dahua.lib.NetSDKLib.NET_MAX_MODE_LEN];
}

