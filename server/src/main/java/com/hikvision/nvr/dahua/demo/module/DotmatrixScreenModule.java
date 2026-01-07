package com.hikvision.nvr.dahua.demo.module;

import com.hikvision.nvr.dahua.lib.NetSDKLib.NET_CTRL_SET_PARK_INFO;

public class DotmatrixScreenModule {
	
	
	public static boolean setDotmatrixScreen(int emType, NET_CTRL_SET_PARK_INFO msg) {
		
		boolean ret = LoginModule.netsdk.CLIENT_ControlDevice(LoginModule.m_hLoginHandle, emType, msg.getPointer(), 3000);
		
		return ret;	
	}
}
