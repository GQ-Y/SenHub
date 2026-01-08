package com.digital.video.gateway.dahua.lib.structure;


import com.digital.video.gateway.dahua.lib.NetSDKLib;
import com.sun.jna.Pointer;

/** 
* @author 291189
* @description  接口 CLIENT_AttachHygrothermograph 的输入参数 
* @origin autoTool
* @date 2023/12/02 10:25:56
*/
public class NET_IN_ATTACH_HYGROTHERMOGRAPH extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
/** 
/ 此结构体大小,必须赋值
*/
    public			int            dwSize;
/** 
/ 回调函数,通知温湿度信息
*/
    public			com.digital.video.gateway.dahua.lib.NetSDKLib.fNotifyHygrothermograph cbNotifyHygrothermograph;
/** 
/ 用户自定义参数
*/
    public Pointer          dwUser;
/** 
/ 过滤器个数
*/
    public			int            nFilterCount;
/** 
/ 过滤器
*/
    public			NET_ATTACH_HYGROTHERMOGRAPH_FILTER_INFO[] stuFilter = new NET_ATTACH_HYGROTHERMOGRAPH_FILTER_INFO[256];

public			NET_IN_ATTACH_HYGROTHERMOGRAPH(){
		for(int i=0;i<stuFilter.length;i++){
			stuFilter[i]=new NET_ATTACH_HYGROTHERMOGRAPH_FILTER_INFO();
			}
		this.dwSize=this.size();
}
}

