package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;

/**
 * @author 251823
 * @description 字符串字节数组对象(长度32)
 * @date 2021/01/13
 */
public class StringByteArr32 extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
	/**
	 * 二维数组内字符串对应字节数组
	 */
    public byte[]           data = new byte[32];
}

