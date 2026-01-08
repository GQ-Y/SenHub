package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * @author 251823
 * @description 视频分析能力集
 * @date 2021/01/11
 */
public class VA_CAPS_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
	/**
	 * 支持的场景列表
	 */
    public byte[]           szSceneName = new byte[com.digital.video.gateway.dahua.lib.NetSDKLib.MAX_SCENE_LIST_SIZE*com.digital.video.gateway.dahua.lib.NetSDKLib.MAX_NAME_LEN]; //TODO
	/**
	 * 支持的场景个数
	 */
    public int              nSupportedSceneNum;
	/**
	 * 预留字段
	 */
    public byte[]           byReserved = new byte[4];
}

