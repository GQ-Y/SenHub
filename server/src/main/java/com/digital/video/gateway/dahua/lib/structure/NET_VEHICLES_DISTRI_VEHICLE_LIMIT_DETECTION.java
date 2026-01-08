package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 车辆密度车流拥堵规则报表数据
*/
public class NET_VEHICLES_DISTRI_VEHICLE_LIMIT_DETECTION extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 本次返回的车辆上限规则数据条数
    */
    public int              nDataNum;
    /**
     * 车辆上限检测规则报表数据个数
    */
    public int              nDataListCount;
    /**
     * 车辆上限检测规则报表数据,参见结构体定义 {@link com.netsdk.lib.structure.NET_VEHICLE_LIMIT_DETECTION_DATA_LIST}
    */
    public NET_VEHICLE_LIMIT_DETECTION_DATA_LIST[] stuDataList = new NET_VEHICLE_LIMIT_DETECTION_DATA_LIST[64];
    /**
     * 保留字段
    */
    public byte[]           szReserved = new byte[1024];

    public NET_VEHICLES_DISTRI_VEHICLE_LIMIT_DETECTION() {
        for(int i = 0; i < stuDataList.length; i++){
            stuDataList[i] = new NET_VEHICLE_LIMIT_DETECTION_DATA_LIST();
        }
    }
}

