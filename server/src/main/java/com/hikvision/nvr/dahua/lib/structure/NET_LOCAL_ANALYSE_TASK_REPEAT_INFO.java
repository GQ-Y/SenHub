package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 重复分析信息
*/
public class NET_LOCAL_ANALYSE_TASK_REPEAT_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 目标类型,参见枚举定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.EM_ANALYSE_OBJECT_TYPE}
    */
    public int              emObjectType;
    /**
     * 重复开始时间,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME stuStartTime = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME();
    /**
     * 重复结束时间,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME stuEndTime = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME();
    /**
     * 保留字节
    */
    public byte[]           szReserved = new byte[512];

    public NET_LOCAL_ANALYSE_TASK_REPEAT_INFO() {
    }
}

