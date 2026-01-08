package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;

/**
 * @author ： 291189
 * @since ： Created in 2021/6/30 10:47
 *  CLIENT_SecurityGateAttachAlarmStatistics 输出结构体
 */
public class NET_OUT_SECURITYGATE_ATTACH_ALARM_STATISTICS extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
    public int              dwSize;                               // 赋值为结构体大小

    public NET_OUT_SECURITYGATE_ATTACH_ALARM_STATISTICS(){
        this.dwSize=this.size();
    }
}

