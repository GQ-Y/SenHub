package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 视频参数
*/
public class NET_SC_VIDEO_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 视频编码格式
    */
    public int              nEncodeType;
    /**
     * 宽
    */
    public int              nWidth;
    /**
     * 高
    */
    public int              nHeight;
    /**
     * 帧率
    */
    public int              nFrameRate;
    /**
     * 预留字节
    */
    public int[]            nReserved = new int[6];

    public NET_SC_VIDEO_INFO() {
    }
}

