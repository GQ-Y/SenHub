package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * CLIENT_SetTourSource 接口输出参数(设置窗口轮巡显示源)
*/
public class NET_OUT_SET_TOUR_SOURCE extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    public int              dwSize;

    public NET_OUT_SET_TOUR_SOURCE() {
        this.dwSize = this.size();
    }
}

