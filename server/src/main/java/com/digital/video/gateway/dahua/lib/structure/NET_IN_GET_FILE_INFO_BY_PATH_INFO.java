package com.digital.video.gateway.dahua.lib.structure;


import com.digital.video.gateway.dahua.lib.NetSDKLib;

/** 
* @author 291189
* @description  CLIENT_FileManagerGetExFileInfoByPath接口入参 
* @date 2022/09/22 19:10:12
*/
public class NET_IN_GET_FILE_INFO_BY_PATH_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
/** 
此结构体大小,必须赋值
*/
    public			int            dwSize;
/** 
文件路径
*/
    public			byte[]         szPath = new byte[256];

public NET_IN_GET_FILE_INFO_BY_PATH_INFO(){
		this.dwSize=this.size();
}
}

