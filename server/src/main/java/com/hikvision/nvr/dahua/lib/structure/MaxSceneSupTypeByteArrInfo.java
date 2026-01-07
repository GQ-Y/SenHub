package com.hikvision.nvr.dahua.lib.structure;

import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * @author 251823
 * @description 场景子类型字符串字节数组对象
 * @date 2021/01/13
 */
public class MaxSceneSupTypeByteArrInfo extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
	/**
	 * 二维数组内字符串对应字节数组
	 */
    public byte[]           sceneSupType = new byte[com.hikvision.nvr.dahua.lib.NetSDKLib.MAX_SCENE_SUBTYPE_LEN];
}

