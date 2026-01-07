package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * CLIENT_RadiometryGetRandomRegionTemper 入参
*/
public class NET_IN_RADIOMETRY_RANDOM_REGION_TEMPER extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 结构体大小
    */
    public int              dwSize;
    /**
     * 通道号
    */
    public int              nChannel;
    /**
     * 有效坐标个数
    */
    public int              nPointNum;
    /**
     * 测温区域的坐标, 8192坐标系,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_POINT}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_POINT[] stuPolygon = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_POINT[8];

    public NET_IN_RADIOMETRY_RANDOM_REGION_TEMPER() {
        this.dwSize = this.size();
        for(int i = 0; i < stuPolygon.length; i++){
            stuPolygon[i] = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_POINT();
        }
    }
}

