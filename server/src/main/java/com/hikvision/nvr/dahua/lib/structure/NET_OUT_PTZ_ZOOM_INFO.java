package com.hikvision.nvr.dahua.lib.structure;

import com.hikvision.nvr.dahua.lib.NetSDKLib;

/**
 * @author 260611
 * @description 获取云台光学变倍信息,对应命令 NET_DEVSTATE_PTZ_ZOOM_INFO
 * @origin autoTool
 * @date 2023/10/16 14:16:51
 */
public class NET_OUT_PTZ_ZOOM_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
    public int              dwSize;
	/**
	 *  光学变倍值
	 */
    public int              nZoomValue;

	public NET_OUT_PTZ_ZOOM_INFO() {
		this.dwSize = this.size();
	}
}

