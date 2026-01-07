package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * CLIENT_AttachRadarAISInfo接口出参
*/
public class NET_OUT_ATTACH_RADAR_AIS_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 结构体大小
    */
    public int              dwSize;

    public NET_OUT_ATTACH_RADAR_AIS_INFO() {
        this.dwSize = this.size();
    }
}

