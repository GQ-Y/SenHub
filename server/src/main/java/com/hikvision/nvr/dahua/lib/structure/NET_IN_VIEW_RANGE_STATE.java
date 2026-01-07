package com.hikvision.nvr.dahua.lib.structure;

import com.hikvision.nvr.dahua.lib.NetSDKLib;
import com.sun.jna.Pointer;

/**
 * @author 260611
 * @description 订阅可视域输入参数
 * @origin autoTool
 * @date 2023/05/30 10:04:52
 */
public class NET_IN_VIEW_RANGE_STATE extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
    public int              dwSize;
	/**
	 * 云台通道
	 */
    public int              nChannel;
	/**
	 * 状态回调函数
	 */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.fViewRangeStateCallBack cbViewRange;
	/**
	 * 用户数据
	 */
    public Pointer          dwUser;

	public NET_IN_VIEW_RANGE_STATE() {
		this.dwSize = this.size();
	}
}

