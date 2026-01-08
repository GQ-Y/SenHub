package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * CLIENT_DoFindVideoStatisticsService 输入参数
*/
public class NET_IN_DO_FIND_VIDEO_STATISTICS_SERVICE extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 此结构体大小,必须赋值
    */
    public int              dwSize;
    /**
     * 查询起始序号
    */
    public int              nBeginNumber;
    /**
     * 本次查询需要获取的条数，一次最多查64条记录
    */
    public int              nCount;

    public NET_IN_DO_FIND_VIDEO_STATISTICS_SERVICE() {
        this.dwSize = this.size();
    }
}

