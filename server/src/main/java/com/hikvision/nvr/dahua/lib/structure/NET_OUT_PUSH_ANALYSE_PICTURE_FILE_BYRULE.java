package com.hikvision.nvr.dahua.lib.structure;


import com.hikvision.nvr.dahua.lib.NetSDKLib;

/** 
* @author 291189
* @description  CLIENT_PushAnalysePictureFileByRule 接口输出参数 
* @date 2022/06/28 19:02:52
*/
public class NET_OUT_PUSH_ANALYSE_PICTURE_FILE_BYRULE extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
/** 
结构体大小
*/
    public			int            dwSize;

public NET_OUT_PUSH_ANALYSE_PICTURE_FILE_BYRULE(){
		this.dwSize=this.size();
}
}

