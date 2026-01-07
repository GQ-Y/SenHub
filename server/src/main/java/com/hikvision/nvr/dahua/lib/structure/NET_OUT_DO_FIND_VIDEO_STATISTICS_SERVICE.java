package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * CLIENT_DoFindVideoStatisticsService 输出参数
*/
public class NET_OUT_DO_FIND_VIDEO_STATISTICS_SERVICE extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 此结构体大小,必须赋值
    */
    public int              dwSize;
    /**
     * 查询到的条数
    */
    public int              nFound;
    /**
     * 统计信息,参见结构体定义 {@link com.netsdk.lib.structure.NET_VIDEO_STATISTICS_SERVICE_INFO}
    */
    public NET_VIDEO_STATISTICS_SERVICE_INFO stuInfo = new NET_VIDEO_STATISTICS_SERVICE_INFO();

    public NET_OUT_DO_FIND_VIDEO_STATISTICS_SERVICE() {
        this.dwSize = this.size();
    }
}

