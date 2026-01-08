package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * CLIENT_StartFindVideoStatisticsService 接口输出参数
*/
public class NET_OUT_START_FIND_VIDEO_STATISTICS_SERVICE extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 此结构体大小,必须赋值
    */
    public int              dwSize;
    /**
     * 查找总条数
    */
    public int              nTotalCount;

    public NET_OUT_START_FIND_VIDEO_STATISTICS_SERVICE() {
        this.dwSize = this.size();
    }
}

