package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;

/**
 * @author 251823
 * @description 情景详细信息
 * @date 2023/03/15 20:39:47
 */
public class CFG_PROFILE_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
	/**
	 * 情景ID
	 */
    public int              nSceneID;
	/**
	 * 厂家名称
	 */
    public byte[]           szBrand = new byte[64];
	/**
	 * 情景模式 {@link com.netsdk.lib.enumeration.EM_SMARTHOME_SCENE_MODE}
	 */
    public int              emScene;
	/**
	 * 串口地址
	 */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.CFG_COMMADDR_INFO stuCommAddr = new com.digital.video.gateway.dahua.lib.NetSDKLib.CFG_COMMADDR_INFO();

	public CFG_PROFILE_INFO() {
	}
}

