package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * CLIENT_StartFindVehicleFlowStat 接口输入参数
*/
public class NET_IN_START_FIND_VEHICLE_FLOW_STAT extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 此结构体大小,必须赋值
    */
    public int              dwSize;
    /**
     * 查询条件,参见结构体定义 {@link com.netsdk.lib.structure.NET_VEHICLE_FLOW_STAT_CONDITION}
    */
    public NET_VEHICLE_FLOW_STAT_CONDITION stuCondition = new NET_VEHICLE_FLOW_STAT_CONDITION();

    public NET_IN_START_FIND_VEHICLE_FLOW_STAT() {
        this.dwSize = this.size();
    }
}

