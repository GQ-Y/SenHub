package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;

/**
 * @author 251823
 * @description 动态能力集
 * @date 2021/01/11
 */
public class VA_DYNAMIC_CAPS_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
	/**
	 * 支持的场景类型,参考{ @link com.digital.video.gateway.dahua.lib.NetSDKLib.EM_SCENE_TYPE}
	 */
    public int[]            emSceneType = new int[32];
	/**
	 * 支持的场景个数
	 */
    public int              nSceneNum;
	/**
	 * 预留字段
	 */
    public byte[]           byReserved = new byte[252];
}

