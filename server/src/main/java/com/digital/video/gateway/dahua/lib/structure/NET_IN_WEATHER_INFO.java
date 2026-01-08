package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
import com.sun.jna.Pointer;
/**
 * 订阅气象信息输入参数
*/
public class NET_IN_WEATHER_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    public int              dwSize;
    /**
     * 云台通道
    */
    public int              nChannel;
    /**
     * 回调函数,参见回调函数定义 {@link com.netsdk.lib.com.digital.video.gateway.dahua.lib.NetSDKLib.fWeatherInfoCallBack}
    */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.fWeatherInfoCallBack cbWeatherInfo;
    /**
     * 用户数据
    */
    public Pointer          dwUser;

    public NET_IN_WEATHER_INFO() {
        this.dwSize = this.size();
    }
}

