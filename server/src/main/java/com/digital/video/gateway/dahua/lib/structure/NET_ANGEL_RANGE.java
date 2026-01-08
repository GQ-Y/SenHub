package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 目标抓拍角度范围
*/
public class NET_ANGEL_RANGE extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
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

