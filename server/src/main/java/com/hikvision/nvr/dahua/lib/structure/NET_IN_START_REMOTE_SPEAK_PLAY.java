package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * CLIENT_StartRemoteSpeakPlay 接口输入参数
*/
public class NET_IN_START_REMOTE_SPEAK_PLAY extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 结构体大小
    */
    public int              dwSize;
    /**
     * 本地通道号
    */
    public int              nChannel;
    /**
     * 音频文件路径
    */
    public byte[]           szPath = new byte[256];

    public NET_IN_START_REMOTE_SPEAK_PLAY() {
        this.dwSize = this.size();
    }
}

