package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 音频参数
*/
public class NET_SC_AUDIO_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 音频编码格式
    */
    public int              nEncodeType;
    /**
     * 通道数
    */
    public int              nChannel;
    /**
     * 采样率
    */
    public int              nSampleRate;
    /**
     * 采样位数
    */
    public int              nBitPerSample;
    /**
     * 预留字节
    */
    public int[]            nReserved = new int[6];

    public NET_SC_AUDIO_INFO() {
    }
}

