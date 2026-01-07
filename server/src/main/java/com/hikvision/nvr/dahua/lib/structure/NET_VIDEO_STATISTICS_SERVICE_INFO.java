package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 统计信息
*/
public class NET_VIDEO_STATISTICS_SERVICE_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 人群分布图规则报表数据,参见结构体定义 {@link com.netsdk.lib.structure.NET_CROWD_DISTRI_MAP_INFO}
    */
    public NET_CROWD_DISTRI_MAP_INFO stuCrowdDistriMap = new NET_CROWD_DISTRI_MAP_INFO();
    /**
     * 车辆分布规则报表数据,参见结构体定义 {@link com.netsdk.lib.structure.NET_VEHICLES_DISTRI_INFO}
    */
    public NET_VEHICLES_DISTRI_INFO stuVehiclesDistri = new NET_VEHICLES_DISTRI_INFO();
    /**
     * 保留字段
    */
    public byte[]           szReserved = new byte[1024];

    public NET_VIDEO_STATISTICS_SERVICE_INFO() {
    }
}

