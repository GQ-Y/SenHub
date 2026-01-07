package com.hikvision.nvr.dahua.lib.structure;

import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * @author 251823
 * @description 图路径对象
 * @date 2021/02/23
 */
public class ObjectPath extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
	/**
	 *  路径字节数组
	 */
    public byte[]           objectPath = new byte[com.hikvision.nvr.dahua.lib.NetSDKLib.MAX_PATH];
}

