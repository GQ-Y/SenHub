package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * CLIENT_StartFindObjectFavoritesLibrary 接口输出参数
*/
public class NET_OUT_START_FIND_OBJECT_FAVORITES_LIBRARY extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 结构体大小
    */
    public int              dwSize;
    /**
     * 查询到记录数
    */
    public int              nTotalCount;

    public NET_OUT_START_FIND_OBJECT_FAVORITES_LIBRARY() {
        this.dwSize = this.size();
    }
}

