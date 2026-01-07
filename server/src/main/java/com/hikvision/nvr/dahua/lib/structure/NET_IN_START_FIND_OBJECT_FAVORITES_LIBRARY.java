package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * CLIENT_StartFindObjectFavoritesLibrary 接口输入参数
*/
public class NET_IN_START_FIND_OBJECT_FAVORITES_LIBRARY extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 结构体大小
    */
    public int              dwSize;
    /**
     * 收藏的开始时间点,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME stuCollectBegin = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME();
    /**
     * 收藏的结束时间点,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME stuCollectEnd = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME();
    /**
     * 目标类型列表个数
    */
    public int              nObjectTypesCount;
    /**
     * 目标类型列表, 0 人脸, 1 人体, 2 机动车, 3 非机动车, 4 动物
    */
    public int[]            nObjectTypes = new int[32];
    /**
     * 排序方式, 0:未知, 1:按收藏时间升序, 2:按收藏时间降序
    */
    public int              nOrderBy;

    public NET_IN_START_FIND_OBJECT_FAVORITES_LIBRARY() {
        this.dwSize = this.size();
    }
}

