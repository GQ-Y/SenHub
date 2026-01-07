package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
import com.sun.jna.Pointer;
/**
 * 窗口轮巡显示源信息
*/
public class NET_SPLIT_TOUR_SOURCE extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    public int              dwSize;
    /**
     * 显示源数组, 用户分配内存,大小为sizeof(DH_SPLIT_SOURCE)*nMaxSrcCount,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_SPLIT_SOURCE}
    */
    public Pointer          pstuSrcs;
    /**
     * 显示源最大数量
    */
    public int              nMaxSrcCount;
    /**
     * 返回的显示源数量
    */
    public int              nRetSrcCount;

    public NET_SPLIT_TOUR_SOURCE() {
        this.dwSize = this.size();
    }
}

