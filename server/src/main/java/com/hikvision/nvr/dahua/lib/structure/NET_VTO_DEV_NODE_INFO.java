package com.hikvision.nvr.dahua.lib.structure;

import com.hikvision.nvr.dahua.lib.NetSDKLib;

/**
 * @author 260611
 * @description 设备树的节点信息
 * @origin autoTool
 * @date 2023/10/19 14:16:47
 */
public class NET_VTO_DEV_NODE_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
	/**
	 * / 节点的树的ID
	 */
    public byte[]           szID = new byte[32];
	/**
	 * / 节点的别名
	 */
    public byte[]           szName = new byte[64];
	/**
	 * / 父节点的ID
	 */
    public byte[]           szParentID = new byte[32];
	/**
	 * / 保留字节
	 */
    public byte[]           szResvered = new byte[256];

	public NET_VTO_DEV_NODE_INFO() {
	}
}

