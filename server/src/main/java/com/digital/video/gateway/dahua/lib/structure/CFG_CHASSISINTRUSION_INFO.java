package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;

/**
 * @author 251823
 * @description 机箱入侵报警(防拆报警)配置
 * @date 2023/03/15 21:57:49
 */
public class CFG_CHASSISINTRUSION_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
	/**
	 * 使能开关
	 */
    public int              bEnable;
	/**
	 * 报警联动
	 */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.CFG_ALARM_MSG_HANDLE stuEventHandler = new com.digital.video.gateway.dahua.lib.NetSDKLib.CFG_ALARM_MSG_HANDLE();

	public CFG_CHASSISINTRUSION_INFO() {
	}
}

