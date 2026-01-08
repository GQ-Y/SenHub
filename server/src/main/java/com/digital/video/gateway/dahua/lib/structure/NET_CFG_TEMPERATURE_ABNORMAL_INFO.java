package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 温度异常报警相关配置信息
*/
public class NET_CFG_TEMPERATURE_ABNORMAL_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 温度异常报警使能
    */
    public int              bEnabled;
    /**
     * 温度报警阈值，单位摄氏度
    */
    public int              nMaxTemperature;
    /**
     * 重复报警时间
    */
    public int              nReportInterval;
    /**
     * 温度校准值，单位摄氏度
    */
    public int              nTemperatureCalibration;
    /**
     * 预留字节
    */
    public byte[]           szReserved = new byte[512];

    public NET_CFG_TEMPERATURE_ABNORMAL_INFO() {
    }
}

