package com.hikvision.nvr.dahua.lib.structure;


import com.hikvision.nvr.dahua.lib.NetSDKLib;

/** 
* @author 291189
* @description CLIENT_SetConsumeResult 输出结构体
* @date 2022/03/07 17:37:19
*/
public class NET_OUT_SET_CONSUME_RESULT extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
/** 
结构体大小
*/
    public			int            dwSize;

public NET_OUT_SET_CONSUME_RESULT(){
this.dwSize=this.size();
}
}

