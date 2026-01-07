package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * CLIENT_FuelPowerCutoffCommand 接口输出参数
*/
public class NET_OUT_FUEL_POWER_CUTOFF_COMMAND extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 此结构体大小，必须赋值
    */
    public int              dwSize;

    public NET_OUT_FUEL_POWER_CUTOFF_COMMAND() {
        this.dwSize = this.size();
    }
}

