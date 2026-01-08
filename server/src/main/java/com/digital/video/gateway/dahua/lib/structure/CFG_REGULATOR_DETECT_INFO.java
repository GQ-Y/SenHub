package com.digital.video.gateway.dahua.lib.structure;


import com.digital.video.gateway.dahua.lib.NetSDKLib;

/** 
* @author 291189
* @description  标准黑体源异常报警配置 
* @date 2022/07/23 10:52:36
*/
public class CFG_REGULATOR_DETECT_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
/** 
使能开关
*/
    public			int            bEnable;
/** 
灵敏度, 1-100
*/
    public			int            nSensitivity;
/** 
报警联动
*/
    public com.digital.video.gateway.dahua.lib.NetSDKLib.CFG_ALARM_MSG_HANDLE stuEventHandler = new com.digital.video.gateway.dahua.lib.NetSDKLib.CFG_ALARM_MSG_HANDLE();

public CFG_REGULATOR_DETECT_INFO(){
}
}

