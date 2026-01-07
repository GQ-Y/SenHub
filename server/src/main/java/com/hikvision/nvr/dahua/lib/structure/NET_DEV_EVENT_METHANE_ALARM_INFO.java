package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 事件类型 EVENT_IVS_ALARM_METHANE_ALARM (甲烷报警事件)对应的数据块描述信息
*/
public class NET_DEV_EVENT_METHANE_ALARM_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 通道号
    */
    public int              nChannelID;
    /**
     * 事件动作,  0:脉冲 1:开始 2:停止
    */
    public int              nAction;
    /**
     * 事件发生的时间,带时区偏差,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME_EX}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME_EX stuTime = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME_EX();
    /**
     * 报警时的甲烷浓度，单位ppm.m
    */
    public int              nConcentration;
    /**
     * 报警阈值，超过该值报警，单位ppm.m
    */
    public int              nConcentrationThreshold;
    /**
     * 上报类型,参见枚举定义 {@link com.netsdk.lib.enumeration.EM_REPORT_TYPE}
    */
    public int              emReportType;
    /**
     * 报警时的预置点ID
    */
    public int              nPresetID;
    /**
     * 报警时的预置点名称
    */
    public byte[]           szPresetName = new byte[64];
    /**
     * 字节对齐
    */
    public byte[]           szReserved1 = new byte[4];
    /**
     * 报警时的甲烷浓度，单位%LEL.m
    */
    public double           dbConcentrationLEL;
    /**
     * 保留字节
    */
    public byte[]           szReserved = new byte[936];

    public NET_DEV_EVENT_METHANE_ALARM_INFO() {
    }
}

