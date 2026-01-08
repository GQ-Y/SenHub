package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;

/**
 * @author 251823
 * @description EM_MSGROUP_OPERATE_SLAVE_POSITION_TO_MASTER 出参
 * @date 2022/09/14 14:09:33
 */
public class NET_OUT_MSGROUP_SLAVE_POSITION_TO_MASTER_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
	/**
	 * 结构体大小
	 */
    public int              dwSize;
	/**
	 * 从机画面上的物体坐标在主机画面上的位置，8192坐标系
	 */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.NET_RECT stuRect = new com.digital.video.gateway.dahua.lib.NetSDKLib.NET_RECT();

	public NET_OUT_MSGROUP_SLAVE_POSITION_TO_MASTER_INFO() {
		this.dwSize = this.size();
	}
}

