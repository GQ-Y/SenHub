package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * @author 251823
 * @description 图路径对象
 * @date 2021/02/23
 */
public class ObjectPath extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
	/**
	 *  路径字节数组
	 */
    public byte[]           objectPath = new byte[com.digital.video.gateway.dahua.lib.NetSDKLib.MAX_PATH];
}

