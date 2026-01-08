package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * CLIENT_GetXRaySourceCumulateTime 接口输出参数
*/
public class NET_OUT_GET_XRAY_SOURCE_CUMULATE_TIME_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 此结构体大小,必须赋值
    */
    public int              dwSize;
    /**
     * 射线源累积出束时间（单位：秒）
    */
    public int              nTime;

    public NET_OUT_GET_XRAY_SOURCE_CUMULATE_TIME_INFO() {
        this.dwSize = this.size();
    }
}

