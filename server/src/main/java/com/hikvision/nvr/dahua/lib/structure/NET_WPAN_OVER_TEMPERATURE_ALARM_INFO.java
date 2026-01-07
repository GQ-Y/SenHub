package com.hikvision.nvr.dahua.lib.structure;


import com.hikvision.nvr.dahua.lib.NetSDKLib;

/** 
* @author 291189
* @description  超温报警 
* @date 2022/08/31 14:44:17
*/
public class NET_WPAN_OVER_TEMPERATURE_ALARM_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
/** 
超温报警使能开关
*/
    public			int            bEnable;
/** 
超温报警下限温度值
*/
    public			double         dbLowerLimit;
/** 
超温报警上限温度值
*/
    public			double         dbUpperLimit;
/** 
预留字段
*/
    public			byte[]         byReserved = new byte[32];

public			NET_WPAN_OVER_TEMPERATURE_ALARM_INFO(){
}
}

