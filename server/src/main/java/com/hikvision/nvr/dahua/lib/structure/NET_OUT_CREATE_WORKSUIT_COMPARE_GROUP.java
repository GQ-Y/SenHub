package com.hikvision.nvr.dahua.lib.structure;

import com.hikvision.nvr.dahua.lib.NetSDKLib;

/**
 * @author 251823
 * @description CLIENT_CreateWorkSuitCompareGroup 接口输出参数
 * @date 2022/10/08 17:06:16
 */
public class NET_OUT_CREATE_WORKSUIT_COMPARE_GROUP extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
	/**
	 * 结构体大小
	 */
    public int              dwSize;
	/**
	 * 字节对齐
	 */
    public byte[]           szReserved = new byte[4];
	/**
	 * 合规库组ID
	 */
    public byte[]           szGroupID = new byte[64];

	public NET_OUT_CREATE_WORKSUIT_COMPARE_GROUP() {
		this.dwSize = this.size();
	}
}

