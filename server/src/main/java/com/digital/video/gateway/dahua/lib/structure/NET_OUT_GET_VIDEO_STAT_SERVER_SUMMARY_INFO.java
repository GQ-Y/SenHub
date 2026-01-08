package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * CLIENT_GetVideoStatServerSummary 接口输出参数
*/
public class NET_OUT_GET_VIDEO_STAT_SERVER_SUMMARY_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 此结构体大小,必须赋值
    */
    public int              dwSize;
    /**
     * 人数统计摘要信息,参见结构体定义 {@link com.netsdk.lib.com.digital.video.gateway.dahua.lib.NetSDKLib.NET_VIDEOSTAT_SUMMARY}
    */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.NET_VIDEOSTAT_SUMMARY stuSummaryInfo = new com.digital.video.gateway.dahua.lib.NetSDKLib.NET_VIDEOSTAT_SUMMARY();

    public NET_OUT_GET_VIDEO_STAT_SERVER_SUMMARY_INFO() {
        this.dwSize = this.size();
    }
}

