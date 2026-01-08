package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;

/**
 * @author 251823
 * @description CLIENT_AttachVideoStatistics 输出参数
 * @date 2022/10/28 10:52:27
 */
public class NET_OUT_ATTACH_VIDEO_STATISTICS extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
	/**
	 * 结构体大小
	 */
    public int              dwSize;

	public NET_OUT_ATTACH_VIDEO_STATISTICS() {
		this.dwSize = this.size();
	}
}

