package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;

/**
 * @author 251823
 * @version 1.0
 * @description 设置停车规则接口，CLIENT_SetParkingRule 出参
 * @date 2021/05/28
 */
public class NET_OUT_SET_PARKINGRULE_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
	/**
	 * 结构体大小
	 */
    public int              dwSize;

	public NET_OUT_SET_PARKINGRULE_INFO() {
		this.dwSize = this.size();
	}
}

