package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 音频格式
*/
public class NET_SPEAK_AUDIO_FORMAT extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 音频编码格式,参见枚举定义 {@link com.netsdk.lib.enumeration.EM_SPEAK_AUDIO_TYPE}
    */
    public int              emFormat;
    /**
     * 预留
    */
    public byte[]           byReserved = new byte[1020];

    public NET_SPEAK_AUDIO_FORMAT() {
    }
}

