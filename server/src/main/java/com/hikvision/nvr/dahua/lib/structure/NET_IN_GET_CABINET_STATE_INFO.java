package com.hikvision.nvr.dahua.lib.structure;


import com.hikvision.nvr.dahua.lib.NetSDKLib;

/** 
* @author 291189
* @description   CLIENT_GetFinancialCabinetState 的输入参数 
* @date 2022/08/10 13:37:59
*/
public class NET_IN_GET_CABINET_STATE_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
/** 
结构体大小
*/
    public			int            dwSize;

public NET_IN_GET_CABINET_STATE_INFO(){
		this.dwSize=this.size();
}
}

