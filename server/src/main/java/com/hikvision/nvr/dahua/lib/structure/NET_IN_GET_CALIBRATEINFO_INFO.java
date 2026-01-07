package com.hikvision.nvr.dahua.lib.structure;

import com.hikvision.nvr.dahua.lib.NetSDKLib;

/**
 * @author 260611
 * @description 获取标定点信息入参
 * @date 2023/05/24 10:24:50
 */
public class NET_IN_GET_CALIBRATEINFO_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
	/**
	 * 结构体大小
	 */
    public int              dwSize;
	/**
	 * 标定点编号
	 */
    public int              nID;
	/**
	 * 通道号
	 */
    public int              nChannel;

	public NET_IN_GET_CALIBRATEINFO_INFO() {
		this.dwSize = this.size();
	}
}

