package com.hikvision.nvr.dahua.lib.structure;


import com.hikvision.nvr.dahua.lib.NetSDKLib;

/** 
* @author 291189
* @description  盖板检测结果 
* @date 2022/06/28 19:44:56
*/
public class NET_COVER_PLATE_DETECT extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
/** 
盖板状态 {@link com.netsdk.lib.enumeration.EM_COVER_PLATE_STATE}
*/
    public			int            emCoverPlateState;
/** 
包围盒
*/
    public NET_RECT         stuBoundingBox = new NET_RECT();

public			NET_COVER_PLATE_DETECT(){
}
}

