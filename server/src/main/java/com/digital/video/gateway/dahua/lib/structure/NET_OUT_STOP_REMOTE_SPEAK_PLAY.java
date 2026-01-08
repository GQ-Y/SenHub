package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * CLIENT_StopRemoteSpeakPlay 接口输出参数
*/
public class NET_OUT_STOP_REMOTE_SPEAK_PLAY extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 结构体大小
    */
    public int              dwSize;

    public NET_OUT_STOP_REMOTE_SPEAK_PLAY() {
        this.dwSize = this.size();
    }
}

