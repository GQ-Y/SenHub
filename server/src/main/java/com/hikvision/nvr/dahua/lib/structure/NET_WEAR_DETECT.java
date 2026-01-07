package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 穿戴检测结果
*/
public class NET_WEAR_DETECT extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 包围盒,参见结构体定义 {@link com.netsdk.lib.structure.NET_RECT}
    */
    public NET_RECT         stuBoundingBox = new NET_RECT();
    /**
     * 穿戴状态 0:未知 1:工作服 2:安全帽
    */
    public int              nState;
    /**
     * 预留字段
    */
    public byte[]           bReserved = new byte[60];

    public NET_WEAR_DETECT() {
    }
}

