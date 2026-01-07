package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 车辆分布规则报表数据
*/
public class NET_VEHICLES_DISTRI_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 车辆密度车流拥堵规则报表数据,参见结构体定义 {@link com.netsdk.lib.structure.NET_VEHICLES_DISTRI_CONGESTION_DETECTION}
    */
    public NET_VEHICLES_DISTRI_CONGESTION_DETECTION stuCongestionDetection = new NET_VEHICLES_DISTRI_CONGESTION_DETECTION();
    /**
     * 车辆上限检测规则报表数据,参见结构体定义 {@link com.netsdk.lib.structure.NET_VEHICLES_DISTRI_VEHICLE_LIMIT_DETECTION}
    */
    public NET_VEHICLES_DISTRI_VEHICLE_LIMIT_DETECTION stuVehicleLimitDetection = new NET_VEHICLES_DISTRI_VEHICLE_LIMIT_DETECTION();
    /**
     * 保留字段
    */
    public byte[]           szReserved = new byte[1024];

    public NET_VEHICLES_DISTRI_INFO() {
    }
}

