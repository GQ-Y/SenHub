package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 视频编码物件配置(对应结构体 CFG_VIDEO_WIDGET2_INFO)
*/
public class CFG_VIDEO_WIDGET2_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 行间距倍数，倍数的基准默认是当前字体高度的十分之一，取值范围为0~5，默认值为0
    */
    public int              nOSDLineSpacing;

    public CFG_VIDEO_WIDGET2_INFO() {
    }
}

