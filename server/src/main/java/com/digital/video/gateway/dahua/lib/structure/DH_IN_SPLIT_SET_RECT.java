package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;

/**
 * @author 251823
 * @description CLIENT_SetSplitWindowRect输入参数(设置窗口位置)
 * @date 2023/06/13 14:11:13
 */
public class DH_IN_SPLIT_SET_RECT extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
    public int              dwSize;
	/**
	 * 通道号(屏号)
	 */
    public int              nChannel;
	/**
	 * 窗口序号
	 */
    public int              nWindowID;
	/**
	 * 窗口位置, 0~8191
	 */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.DH_RECT stuRect = new com.digital.video.gateway.dahua.lib.NetSDKLib.DH_RECT();
	/**
	 * 坐标是否满足直通条件, 直通是指拼接屏方式下,此窗口区域正好为物理屏区域
	 */
    public int              bDirectable;

	public DH_IN_SPLIT_SET_RECT() {
		this.dwSize = this.size();
	}
}

