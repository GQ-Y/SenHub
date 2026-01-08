package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * CLIENT_GetWaterRadarObjectInfo 输入参数
*/
public class NET_IN_GET_WATERRADAR_OBJECTINFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 结构体大小
    */
    public int              dwSize;

    public NET_IN_GET_WATERRADAR_OBJECTINFO() {
        this.dwSize = this.size();
    }
}

