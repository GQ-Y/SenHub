package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 周界主动巡视信息
*/
public class NET_PARTOL_LINKAGE_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 是否开启主动巡视
    */
    public int              bEnable;
    /**
     * 主动巡视类型,参见枚举定义 {@link com.netsdk.lib.enumeration.EM_PATROL_TYPE}
    */
    public int              emPatrolType;
    /**
     * 预置点主动巡视具体信息,参见结构体定义 {@link com.netsdk.lib.structure.NET_PRESET_INFO}
    */
    public NET_PRESET_INFO  stuPreset = new NET_PRESET_INFO();
    /**
     * 预留字节
    */
    public byte[]           szReserved = new byte[1024];

    public NET_PARTOL_LINKAGE_INFO() {
    }
}

