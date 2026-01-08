package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 分组客流统计数据
*/
public class NET_NUMBERSTATGROUP_SUMMARY extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 组ID
    */
    public byte[]           szGroupID = new byte[64];
    /**
     * 组名
    */
    public byte[]           szGroupName = new byte[128];
    /**
     * 通道号数
    */
    public int              nChannelNum;
    /**
     * 通道号列表
    */
    public int[]            arChannelList = new int[1024];
    /**
     * 统计时间秒数，已包含时区偏移,参见结构体定义 {@link com.netsdk.lib.com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TIME_EX}
    */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TIME_EX stuUTC = new com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TIME_EX();
    /**
     * 时区索引号
    */
    public int              nTimeZone;
    /**
     * 进入小计,参见结构体定义 {@link com.netsdk.lib.structure.NET_NUMBERSTATGROUP_SUBTOTAL}
    */
    public NET_NUMBERSTATGROUP_SUBTOTAL stuEnteredSubtotal = new NET_NUMBERSTATGROUP_SUBTOTAL();
    /**
     * 出去小计,参见结构体定义 {@link com.netsdk.lib.structure.NET_NUMBERSTATGROUP_SUBTOTAL}
    */
    public NET_NUMBERSTATGROUP_SUBTOTAL stuExitedSubtotal = new NET_NUMBERSTATGROUP_SUBTOTAL();
    /**
     * 区域内人数
    */
    public int              nInsideSubtotal;
    /**
     * 保留
    */
    public byte[]           byReserved = new byte[1024];

    public NET_NUMBERSTATGROUP_SUMMARY() {
    }
}

