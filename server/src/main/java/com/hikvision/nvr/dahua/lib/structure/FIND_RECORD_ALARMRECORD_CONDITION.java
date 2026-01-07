package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 报警记录信息查询条件
*/
public class FIND_RECORD_ALARMRECORD_CONDITION extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    public int              dwSize;
    /**
     * 启用时间段查询
    */
    public int              bTimeEnable;
    /**
     * 起始时间,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME stStartTime = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME();
    /**
     * 结束时间,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME stEndTime = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME();

    public FIND_RECORD_ALARMRECORD_CONDITION() {
        this.dwSize = this.size();
    }
}

