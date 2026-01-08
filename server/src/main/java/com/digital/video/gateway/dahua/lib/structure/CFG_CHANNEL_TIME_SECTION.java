package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;

/**
 * @author 251823
 * @description 网络存储服务器配置
 * @date 2022/09/08 19:33:10
 */
public class CFG_CHANNEL_TIME_SECTION extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
	/**
	 * 存储时间段
	 */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.CFG_TIME_SECTION[] stuTimeSection = new com.digital.video.gateway.dahua.lib.NetSDKLib.CFG_TIME_SECTION[7 * 2];

	public CFG_CHANNEL_TIME_SECTION() {
		for (int i = 0; i < stuTimeSection.length; i++) {
			stuTimeSection[i] = new com.digital.video.gateway.dahua.lib.NetSDKLib.CFG_TIME_SECTION();
		}
	}
}

