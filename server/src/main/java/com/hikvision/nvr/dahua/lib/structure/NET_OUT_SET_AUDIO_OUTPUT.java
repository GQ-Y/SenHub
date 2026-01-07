package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * CLIENT_SetSplitAudioOuput接口输出参数(设置音频输出模式)
*/
public class NET_OUT_SET_AUDIO_OUTPUT extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    public int              dwSize;

    public NET_OUT_SET_AUDIO_OUTPUT() {
        this.dwSize = this.size();
    }
}

