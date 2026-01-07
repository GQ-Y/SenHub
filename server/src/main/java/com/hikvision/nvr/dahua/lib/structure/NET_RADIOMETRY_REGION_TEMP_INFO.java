package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 随机测温区域的温度信息
*/
public class NET_RADIOMETRY_REGION_TEMP_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 温度单位,参见枚举定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.EM_TEMPERATURE_UNIT}
    */
    public int              emTemperatureUnit;
    /**
     * 获取测温区域平均温度的参数值,精度为0.01,扩大100倍
    */
    public int              nTemperAver;
    /**
     * 获取测温区域最高的温度的参数值,精度为0.01,扩大100倍
    */
    public int              nTemperMax;
    /**
     * 获取测温区域最低的温度的参数值,精度为0.01,扩大100倍
    */
    public int              nTemperMin;
    /**
     * 随机区域内最高的温度的点的坐标,8192坐标系,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_POINT}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_POINT stuTemperMaxPoint = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_POINT();
    /**
     * 随机区域内最低的温度的点的坐标,8192坐标系,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_POINT}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_POINT stuTemperMinPoint = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_POINT();
    /**
     * 保留字节
    */
    public byte[]           byReserved = new byte[256];

    public NET_RADIOMETRY_REGION_TEMP_INFO() {
    }
}

