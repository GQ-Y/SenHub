package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 云台一键锁定
*/
public class CFG_PTZ_ONE_CLICK_LOCK extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 是否支持云台一键锁定
    */
    public int              bSupport;
    /**
     * 云台锁定时和预置点智能是否互斥
    */
    public int              bIsOpposeIntelliPtzPreset;

    public CFG_PTZ_ONE_CLICK_LOCK() {
    }
}

