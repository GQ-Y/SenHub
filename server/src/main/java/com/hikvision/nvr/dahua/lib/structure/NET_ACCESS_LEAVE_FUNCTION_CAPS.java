package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 请假功能组件能力集
*/
public class NET_ACCESS_LEAVE_FUNCTION_CAPS extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 请假数据库表最大容量
    */
    public int              nMaxLeaves;
    /**
     * 请假、销假、删除接口每次调用支持的最大数量
    */
    public int              nMaxOperationRate;
    /**
     * 保留字节
    */
    public byte[]           szReserved = new byte[512];

    public NET_ACCESS_LEAVE_FUNCTION_CAPS() {
    }
}

