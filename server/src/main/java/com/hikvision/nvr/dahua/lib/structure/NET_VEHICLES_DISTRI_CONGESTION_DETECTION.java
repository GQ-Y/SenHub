package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 车辆密度车流拥堵规则报表数据
*/
public class NET_VEHICLES_DISTRI_CONGESTION_DETECTION extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 本次返回的车辆拥堵规则数据条数
    */
    public int              nDataNum;
    /**
     * 车流拥堵规则数据列表个数
    */
    public int              nDataListCount;
    /**
     * 车流拥堵规则数据列表,参见结构体定义 {@link com.netsdk.lib.structure.NET_CONGESTION_DETECTION_DATA_LIST}
    */
    public NET_CONGESTION_DETECTION_DATA_LIST[] stuDataList = new NET_CONGESTION_DETECTION_DATA_LIST[64];
    /**
     * 保留字段
    */
    public byte[]           szReserved = new byte[1024];

    public NET_VEHICLES_DISTRI_CONGESTION_DETECTION() {
        for(int i = 0; i < stuDataList.length; i++){
            stuDataList[i] = new NET_CONGESTION_DETECTION_DATA_LIST();
        }
    }
}

