package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 渗漏检测结果扩展数据
*/
public class NET_LEAKAGE_DETECT_EX extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 渗漏状态:0:未知;1:地面油污;2:部件表面油污,参见枚举定义 {@link com.netsdk.lib.enumeration.EM_LEAKAGE_STATE}
    */
    public int              emLeakageState;
    /**
     * 目标置信度，0~100，越大表示越高
    */
    public int              nConfidence;
    /**
     * 预留字节
    */
    public byte[]           szReserved = new byte[1020];

    public NET_LEAKAGE_DETECT_EX() {
    }
}

