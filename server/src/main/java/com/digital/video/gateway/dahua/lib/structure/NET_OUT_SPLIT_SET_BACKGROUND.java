package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 设置视频输出背景图输出参数
*/
public class NET_OUT_SPLIT_SET_BACKGROUND extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    public int              dwSize;

    public NET_OUT_SPLIT_SET_BACKGROUND() {
        this.dwSize = this.size();
    }
}

