package com.hikvision.nvr.dahua.lib.structure;

import com.hikvision.nvr.dahua.lib.NetSDKLib;

/**
 * @author 251823
 * @description 灯光信息
 * @date 2023/05/23 09:40:10
 */
public class NET_LIGHT_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
	/**
	 * 亮度百分比
	 */
    public int              nLight;
	/**
	 * 激光灯角度归一化值
	 */
    public int              nAngle;

	public NET_LIGHT_INFO() {
	}
}

