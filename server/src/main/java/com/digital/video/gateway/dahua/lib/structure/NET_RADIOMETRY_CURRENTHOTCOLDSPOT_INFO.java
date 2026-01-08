package com.digital.video.gateway.dahua.lib.structure;


import com.digital.video.gateway.dahua.lib.NetSDKLib;


/** 
* @author 291189
* @description  冷（最低的温度）、热（最高的温度）点信息 
* @origin autoTool
* @date 2023/08/07 13:51:18
*/
public class NET_RADIOMETRY_CURRENTHOTCOLDSPOT_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
/** 
热点坐标，范围：【0~8191】
*/
    public com.digital.video.gateway.dahua.lib.NetSDKLib.NET_POINT stuHotPoint = new com.digital.video.gateway.dahua.lib.NetSDKLib.NET_POINT();
/** 
冷点坐标，范围：【0~8191】
*/
    public com.digital.video.gateway.dahua.lib.NetSDKLib.NET_POINT stuColdPoint = new com.digital.video.gateway.dahua.lib.NetSDKLib.NET_POINT();
/** 
热点温度值（最高的温度）
*/
    public			float          fHotSpotValue;
/** 
冷点温度值（最低的温度）
*/
    public			float          fColdSpotValue;
/** 
温度单位，0:摄氏度 1：华氏度
*/
    public			int            nTemperatrueUnit;
/** 
保留字节
*/
    public			byte[]         byReserved = new byte[256];

public			NET_RADIOMETRY_CURRENTHOTCOLDSPOT_INFO(){
}
}

