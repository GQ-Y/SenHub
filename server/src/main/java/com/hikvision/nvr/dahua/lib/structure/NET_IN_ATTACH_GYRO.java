package com.hikvision.nvr.dahua.lib.structure;


import com.hikvision.nvr.dahua.lib.NetSDKLib;
import com.sun.jna.Pointer;

/** 
* @author 291189
* @description  CLIENT_AttachGyro接口入参 
* @date 2022/10/26 15:21:25
*/
public class NET_IN_ATTACH_GYRO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
/** 
此结构体大小,必须赋值
*/
    public			int            dwSize;
/** 
回调函数
*/
    public com.hikvision.nvr.dahua.lib.NetSDKLib.fNotifyGyroData cbNotifyGyroData;
/** 
用户自定义参数
*/
    public Pointer          dwUser;

public NET_IN_ATTACH_GYRO(){
		this.dwSize=this.size();
}
}

