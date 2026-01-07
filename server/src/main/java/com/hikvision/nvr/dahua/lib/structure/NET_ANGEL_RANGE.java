package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 目标抓拍角度范围
*/
public class NET_ANGEL_RANGE extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 角度最小值
    */
    public int              nMin;
    /**
     * 角度最大值
    */
    public int              nMax;

    public NET_ANGEL_RANGE() {
    }
}

