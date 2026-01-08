package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;
import com.sun.jna.Pointer;

/**
 * @author 260611
 * @description CLIENT_AttachTrafficFlowStatRealFlow 输入参数
 * @origin autoTool
 * @date 2023/08/31 14:21:53
 */
public class NET_IN_ATTACH_TRAFFIC_FLOW_STAT_REAL_FLOW extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
	/**
	 * 此结构体大小,必须赋值
	 */
    public int              dwSize;
	/**
	 * 字节对齐
	 */
    public byte[]           szReserved = new byte[4];
	/**
	 * 回调函数
	 */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.fVehicleInOutAnalyseProc cbVehicleInOutAnalyseProc;
	/**
	 * 用户信息
	 */
    public Pointer          dwUser;

	public NET_IN_ATTACH_TRAFFIC_FLOW_STAT_REAL_FLOW() {
		this.dwSize = this.size();
	}
}

