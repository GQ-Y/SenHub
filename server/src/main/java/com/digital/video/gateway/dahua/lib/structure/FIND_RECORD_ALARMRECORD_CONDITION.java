package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 报警记录信息查询条件
*/
public class FIND_RECORD_ALARMRECORD_CONDITION extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    public int              dwSize;
    /**
     * 启用时间段查询
    */
    public int              bTimeEnable;
    /**
     * 起始时间,参见结构体定义 {@link com.netsdk.lib.com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TIME}
    */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TIME stStartTime = new com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TIME();
    /**
     * 结束时间,参见结构体定义 {@link com.netsdk.lib.com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TIME}
    */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TIME stEndTime = new com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TIME();

    public FIND_RECORD_ALARMRECORD_CONDITION() {
        this.dwSize = this.size();
    }
}

