package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;

/**
 * @author 251823
 * @description CLIENT_GetDeviceSerialNo 入参
 * @date 2023/03/15 20:18:02
 */
public class NET_IN_GET_DEVICESERIALNO_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
	/**
	 * 结构体大小
	 */
    public int              dwSize;

	public NET_IN_GET_DEVICESERIALNO_INFO() {
		this.dwSize = this.size();
	}
}

