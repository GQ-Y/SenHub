package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * @author 251823
 * @description 跟随车辆报警自定义信息
 * @date 2021/01/13
 */
public class NET_FOLLOW_CAR_ALARM_CUSTOM_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
	/**
	 * GPS信息
	 */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.NET_GPS_STATUS_INFO stuGPS;
	/**
	 * 保留字节
	 */
    public byte[]           byReserved = new byte[256];
}

