package com.hikvision.nvr.dahua.lib.structure;

import com.hikvision.nvr.dahua.lib.NetSDKLib;

/**
 * @author 260611
 * @description 异常防区信息
 * @origin autoTool
 * @date 2023/08/10 09:52:29
 */
public class NET_ARM_MODE_ZONE_ABNORMAL_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
	/**
	 * 防区名称
	 */
    public byte[]           szName = new byte[32];
	/**
	 * 异常原因
	 */
    public byte[]           szReason = new byte[32];
	/**
	 * 防区号
	 */
    public int              nIndex;
	/**
	 * 保留字节
	 */
    public byte[]           szResvered = new byte[1020];

	public NET_ARM_MODE_ZONE_ABNORMAL_INFO() {
	}
}

