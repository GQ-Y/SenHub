package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;
import com.sun.jna.Pointer;

/**
 * @author 251823
 * @description CLIENT_AttachLowRateWPAN()输入参数
 * @date 2023/03/16 16:17:02
 */
public class NET_IN_ATTACH_LOWRATEWPAN extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
    public int              dwSize;
	/**
	 * 对码数据回调
	 */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.fAttachLowRateWPANCB cbAttachLowRateWPANCB;
	/**
	 * 用户数据
	 */
    public Pointer          dwUser;

	public NET_IN_ATTACH_LOWRATEWPAN() {
		this.dwSize = this.size();
	}
}

