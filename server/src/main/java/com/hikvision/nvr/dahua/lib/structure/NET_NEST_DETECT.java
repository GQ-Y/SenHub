package com.hikvision.nvr.dahua.lib.structure;


import com.hikvision.nvr.dahua.lib.NetSDKLib;

/** 
* @author 291189
* @description  鸟巢检测结果 
* @date 2022/06/28 19:44:54
*/
public class NET_NEST_DETECT extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
/** 
包围盒
*/
    public NET_RECT         stuBoundingBox = new NET_RECT();

public NET_NEST_DETECT(){
}
}

