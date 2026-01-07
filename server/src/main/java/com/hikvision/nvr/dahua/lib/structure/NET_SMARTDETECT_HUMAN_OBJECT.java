package com.hikvision.nvr.dahua.lib.structure;

import com.hikvision.nvr.dahua.lib.NetSDKLib;

/**
 * @author 251823
 * @description 智能动检(人)对象信息
 * @date 2023/05/15 13:47:59
 */
public class NET_SMARTDETECT_HUMAN_OBJECT extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
	/**
	 * 人动检ID
	 */
    public int              nHumanID;
	/**
	 * 人的位置
	 */
    public NET_RECT         stuRect = new NET_RECT();
	/**
	 * 保留字节
	 */
    public byte[]           bReserved = new byte[508];

	public NET_SMARTDETECT_HUMAN_OBJECT() {
	}
}

