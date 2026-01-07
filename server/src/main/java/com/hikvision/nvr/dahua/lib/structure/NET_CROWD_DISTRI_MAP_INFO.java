package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 人群分布图规则报表数据
*/
public class NET_CROWD_DISTRI_MAP_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 人群分布图规则报表数据,参见结构体定义 {@link com.netsdk.lib.structure.NET_CROWD_DISTRI_MAP}
    */
    public NET_CROWD_DISTRI_MAP stuCrowdDistriMap = new NET_CROWD_DISTRI_MAP();
    /**
     * 保留字段
    */
    public byte[]           szReserved = new byte[1024];

    public NET_CROWD_DISTRI_MAP_INFO() {
    }
}

