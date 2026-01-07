package com.hikvision.nvr.dahua.lib.structure;


import com.hikvision.nvr.dahua.lib.NetSDKLib;

/** 
* @author 291189
* @description  CLIENT_GetCustomInfoCaps 输入参数 
* @date 2022/05/11 20:23:41
*/
public class NET_IN_GET_CUSTOMINFO_CAPS extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
/** 
结构体大小
*/
    public			int            dwSize;

public NET_IN_GET_CUSTOMINFO_CAPS(){
		this.dwSize=this.size();
}
}

