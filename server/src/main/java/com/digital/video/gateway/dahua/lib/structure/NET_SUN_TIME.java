package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 大致日出/日落时间
*/
public class NET_SUN_TIME extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 时
    */
    public int              nHour;
    /**
     * 分
    */
    public int              nMinute;
    /**
     * 秒
    */
    public int              nSecond;

    public NET_SUN_TIME() {
    }
}

