package com.hikvision.nvr.dahua.lib.structure;


import com.hikvision.nvr.dahua.lib.NetSDKLib;

/** 
* @author 291189
* @description  图片信息 
* @date 2022/08/31 14:44:17
*/
public class NET_WPAN_ACCESSORY_IMAGE_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
/** 
分辨率 {@link com.netsdk.lib.enumeration.CAPTURE_SIZE}
*/
    public			int            emResolution;
/** 
抓图数量
*/
    public			int            nSnapshotNumber;
/** 
抓图次数
*/
    public			int            nSnapshotTimes;
/** 
预留字段
*/
    public			byte[]         byReserved = new byte[32];

public			NET_WPAN_ACCESSORY_IMAGE_INFO(){
}
}

