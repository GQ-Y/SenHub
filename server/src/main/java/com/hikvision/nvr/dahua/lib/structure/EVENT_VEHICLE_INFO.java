package com.hikvision.nvr.dahua.lib.structure;

import com.hikvision.nvr.dahua.lib.NetSDKLib;

/**
 * @author 260611
 * @description 行人信息
 * @origin autoTool
 * @date 2023/08/01 13:57:45
 */
public class EVENT_VEHICLE_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
	/**
	 * 物体类型，Passerby表示行人
	 */
    public byte[]           szCategory = new byte[64];
	/**
	 * 预留字节
	 */
    public byte[]           byReserved = new byte[64];

	public EVENT_VEHICLE_INFO() {
	}
}

