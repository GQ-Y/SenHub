package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;

/**
 * @author 251823
 * @description CLIENT_GetDeviceType 出参
 * @date 2023/03/15 20:18:34
 */
public class NET_OUT_GET_DEVICETYPE_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
	/**
	 * 结构体大小
	 */
    public int              dwSize;
	/**
	 * 设备类型,该字段被废弃
	 */
    public byte[]           szType = new byte[32];
	/**
	 * 设备类型, 扩展设备类型建议使用此字段
	 */
    public byte[]           szTypeEx = new byte[256];

	public NET_OUT_GET_DEVICETYPE_INFO() {
		this.dwSize = this.size();
	}
}

