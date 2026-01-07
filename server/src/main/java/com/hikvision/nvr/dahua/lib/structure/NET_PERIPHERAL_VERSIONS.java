package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 设备的外设软件版本
*/
public class NET_PERIPHERAL_VERSIONS extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 对应外设的版本信息
    */
    public byte[]           szVersion = new byte[32];
    /**
     * 外设类型,参见枚举定义 {@link com.netsdk.lib.enumeration.EM_PERIPHERAL_TYPE}
    */
    public int              emPeripheralType;
    /**
     * 对应外设版本的编译日期，精确到天
    */
    public byte[]           szBuildDate = new byte[24];
    /**
     * 保留字节
    */
    public byte[]           byReserved = new byte[228];

    public NET_PERIPHERAL_VERSIONS() {
    }
}

