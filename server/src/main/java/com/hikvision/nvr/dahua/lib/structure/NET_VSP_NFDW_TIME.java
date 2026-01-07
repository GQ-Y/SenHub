package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 南网开始升级时
*/
public class NET_VSP_NFDW_TIME extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 年
    */
    public int              nYear;
    /**
     * 月
    */
    public int              nMonth;
    /**
     * 日
    */
    public int              nDay;
    /**
     * 时
    */
    public int              nHour;
    /**
     * 分
    */
    public int              nMinute;
    /**
     * 秒
    */
    public int              nSecond;
    /**
     * 预留字节
    */
    public byte[]           szReserved = new byte[64];

    public NET_VSP_NFDW_TIME() {
    }
}

