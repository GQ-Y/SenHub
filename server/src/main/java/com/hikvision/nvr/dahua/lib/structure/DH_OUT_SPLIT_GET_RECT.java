package com.hikvision.nvr.dahua.lib.structure;

import com.hikvision.nvr.dahua.lib.NetSDKLib;

/**
 * @author 251823
 * @description CLIENT_GetSplitWindowRect接口输出参数(获取窗口位置)
 * @date 2023/06/13 14:09:53
 */
public class DH_OUT_SPLIT_GET_RECT extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
    public int              dwSize;
	/**
	 * 窗口位置, 0~8191
	 */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.DH_RECT stuRect = new com.hikvision.nvr.dahua.lib.NetSDKLib.DH_RECT();

	public DH_OUT_SPLIT_GET_RECT() {
		this.dwSize = this.size();
	}
}

