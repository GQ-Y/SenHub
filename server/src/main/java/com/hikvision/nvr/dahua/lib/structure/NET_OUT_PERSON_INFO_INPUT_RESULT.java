package com.hikvision.nvr.dahua.lib.structure;

import com.hikvision.nvr.dahua.lib.NetSDKLib;

/**
 * CLIENT_SetPersonInfoInputResult 输出参数
 */
public class NET_OUT_PERSON_INFO_INPUT_RESULT extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
	/**
	 * 结构体大小
	 */
    public int              dwSize;

	public NET_OUT_PERSON_INFO_INPUT_RESULT() {
		this.dwSize = this.size();
	}
}

