package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 报警输出时间段控制
*/
public class NET_ALARMOUT_TIME_SCHEDULE_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 分时段报警输出使能
    */
    public int              bEnable;
    /**
     * 预留字节
    */
    public byte[]           szReserved = new byte[1020];

    public NET_ALARMOUT_TIME_SCHEDULE_INFO() {
    }
}

