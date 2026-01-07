package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 上报的雷达AIS信息
*/
public class NET_RADAR_NOTIFY_AIS_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * AIS数据大小
    */
    public int              nDataLength;
    /**
     * AIS数据数组
    */
    public int[]            nData = new int[512];
    /**
     * 订阅ID
    */
    public int              nSID;
    /**
     * 保留字节
    */
    public byte[]           byReserved = new byte[1024];

    public NET_RADAR_NOTIFY_AIS_INFO() {
    }
}

