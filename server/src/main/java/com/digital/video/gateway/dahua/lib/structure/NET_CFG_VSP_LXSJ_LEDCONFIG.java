 package com.digital.video.gateway.dahua.lib.structure;
 import com.digital.video.gateway.dahua.lib.NetSDKLib;

 /**LED屏幕配置  */
public class NET_CFG_VSP_LXSJ_LEDCONFIG extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
/** 车位个数*/
    public			int            nParkingNum;
/** 车位配置*/
    public			NET_CFG_VSP_LXSJ_PARKING[] stuParking = (NET_CFG_VSP_LXSJ_PARKING[])new NET_CFG_VSP_LXSJ_PARKING().toArray(256);
}

