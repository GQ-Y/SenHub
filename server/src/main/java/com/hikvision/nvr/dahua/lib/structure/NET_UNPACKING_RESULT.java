package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 检查结果
*/
public class NET_UNPACKING_RESULT extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 违禁物品分类
    */
    public byte[]           szContrabandClass = new byte[64];
    /**
     * 违禁品数量
    */
    public int              nContrabandNumber;
    /**
     * 违禁品处理方式 -1:未知 0: 自弃 1: 放行 2: 物品保管 3: 移交公安 4: 自行处理 5: 误报
    */
    public int              nProcessMode;
    /**
     * 保留字节
    */
    public byte[]           szReserved = new byte[256];

    public NET_UNPACKING_RESULT() {
    }
}

