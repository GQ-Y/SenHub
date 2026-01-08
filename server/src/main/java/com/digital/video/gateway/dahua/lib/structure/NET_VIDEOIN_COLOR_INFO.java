package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 视频输入颜色配置
*/
public class NET_VIDEOIN_COLOR_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    public int              dwSize;
    /**
     * 配置类型，获取和设置时都要指定,参见枚举定义 {@link com.netsdk.lib.com.digital.video.gateway.dahua.lib.NetSDKLib.NET_EM_CONFIG_TYPE}
    */
    public int              emCfgType;
    /**
     * 亮度0-100
    */
    public int              nBrightness;
    /**
     * 对比度0-100
    */
    public int              nContrast;
    /**
     * 饱和度0-100
    */
    public int              nSaturation;
    /**
     * 伽马值0-100
    */
    public int              nGamma;
    /**
     * 色彩风格,参见枚举定义 {@link com.netsdk.lib.enumeration.EM_COLOR_STYLE_TYPE}
    */
    public int              emColorStyle;
    /**
     * 色调值0-100
    */
    public int              nHue;
    /**
     * 色彩抑制等级值0-100
    */
    public int              nChromaSuppress;
    /**
     * 对应的时间段,参见结构体定义 {@link com.netsdk.lib.com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TSECT}
    */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TSECT stuTimeSection = new com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TSECT();

    public NET_VIDEOIN_COLOR_INFO() {
        this.dwSize = this.size();
    }
}

