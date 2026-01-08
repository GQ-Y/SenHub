package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;

public class NET_OUT_STARTSERACH_DEVICE extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
	/**
	 *  结构体大小
	 */
    public 	int             dwSize;

	public NET_OUT_STARTSERACH_DEVICE()
	    {
	     this.dwSize = this.size();
	    }// 此结构体大小
}

