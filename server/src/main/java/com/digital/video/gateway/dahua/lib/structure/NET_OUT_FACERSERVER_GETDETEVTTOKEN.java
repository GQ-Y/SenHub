package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;

/**
 * @author 251823
 * @description CLIENT_FaceRServerGetDetectToken 接口输出参数
 * @date 2023/04/20 14:04:30
 */
public class NET_OUT_FACERSERVER_GETDETEVTTOKEN extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
	/**
	 * 结构体大小
	 */
    public int              dwSize;
	/**
	 * 获取到的查询令牌
	 */
    public int              nToken;

	public NET_OUT_FACERSERVER_GETDETEVTTOKEN() {
		this.dwSize = this.size();
	}
}

