package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 人员定额消费配置信息
*/
public class NET_CFG_USER_QUOTA_CONSUMPTION_INFO_OBJECT extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 用户类型
    */
    public int              nUserType;
    /**
     * 平台固定消费金额，单位（分）
    */
    public int              nConsumptionAmount;
    /**
     * 预留字节
    */
    public byte[]           szReserved = new byte[1024];

    public NET_CFG_USER_QUOTA_CONSUMPTION_INFO_OBJECT() {
    }
}

