package com.hikvision.nvr.dahua.lib.structure;

import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * @author 251823
 * @description  历史接种日期字符串对应字节数组
 * @date 2021/08/15
 */
public class VaccinateDateByteArr extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
	/**
	 *  历史接种日期字符串对应字节数组
	 */
    public byte[]           vaccinateDateByteArr = new byte[32];
}

