package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;

/**
 * @author 251823
 * @description 增加对码信息
 * @date 2023/03/16 14:52:53
 */
public class NET_CTRL_LOWRATEWPAN_ADD extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
    public int              dwSize;
	/**
	 * 对码数据
	 */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.NET_CODEID_INFO stuCodeIDInfo = new com.digital.video.gateway.dahua.lib.NetSDKLib.NET_CODEID_INFO();

	public NET_CTRL_LOWRATEWPAN_ADD() {
		this.dwSize = this.size();
	}
}

