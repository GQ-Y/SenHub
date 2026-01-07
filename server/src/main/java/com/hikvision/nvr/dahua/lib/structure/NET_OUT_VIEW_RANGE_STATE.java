package com.hikvision.nvr.dahua.lib.structure;

import com.hikvision.nvr.dahua.lib.NetSDKLib;

/**
 * @author 260611
 * @description 订阅可视域输出参数
 * @origin autoTool
 * @date 2023/05/30 10:04:52
 */
public class NET_OUT_VIEW_RANGE_STATE extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
    public int              dwSize;

	public NET_OUT_VIEW_RANGE_STATE() {
		this.dwSize = this.size();
	}
}

