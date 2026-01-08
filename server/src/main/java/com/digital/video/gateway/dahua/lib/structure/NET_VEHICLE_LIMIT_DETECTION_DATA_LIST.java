package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 车辆上限检测规则数据列表
*/
public class NET_VEHICLE_LIMIT_DETECTION_DATA_LIST extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 记录本条数据的UTC时间
    */
    public int              nUTC;
    /**
     * 记录本条数据的本地时间,参见结构体定义 {@link com.netsdk.lib.com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TIME}
    */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TIME stuLocalTime = new com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TIME();
    /**
     * 规则名称
    */
    public byte[]           szRuleName = new byte[32];
    /**
     * 车辆数量
    */
    public int              nVehiclesNum;
    /**
     * 规则名称扩展字段
    */
    public byte[]           szRuleNameEx = new byte[128];
    /**
     * 保留字段
    */
    public byte[]           szReserved = new byte[896];

    public NET_VEHICLE_LIMIT_DETECTION_DATA_LIST() {
    }
}

