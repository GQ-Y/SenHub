package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 收藏结果集合
*/
public class NET_MARK_OBJECT_RESULTS_DATA extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 收藏成功的唯一标识，若值为-1表示收藏失败
    */
    public int              nID;
    /**
     * 保留字段
    */
    public byte[]           szReserved = new byte[1020];

    public NET_MARK_OBJECT_RESULTS_DATA() {
    }
}

