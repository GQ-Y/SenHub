package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 订阅气象信息输出参数
*/
public class NET_OUT_WEATHER_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    public int              dwSize;

    public NET_OUT_WEATHER_INFO() {
        this.dwSize = this.size();
    }
}

