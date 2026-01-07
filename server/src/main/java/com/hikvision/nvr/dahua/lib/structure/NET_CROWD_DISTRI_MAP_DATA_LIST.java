package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 人群分布图数据列表
*/
public class NET_CROWD_DISTRI_MAP_DATA_LIST extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 记录本条数据的UTC时间
    */
    public int              nUTC;
    /**
     * 记录本条数据的本地时间,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME stuLocalTime = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME();
    /**
     * 统计区名称
    */
    public byte[]           szAreaName = new byte[32];
    /**
     * 统计区内人数
    */
    public int              nPeopleNum;
    /**
     * 统计区扩展名称
    */
    public byte[]           szAreaNameEx = new byte[64];
    /**
     * 保留字段
    */
    public byte[]           szReserved = new byte[960];

    public NET_CROWD_DISTRI_MAP_DATA_LIST() {
    }
}

