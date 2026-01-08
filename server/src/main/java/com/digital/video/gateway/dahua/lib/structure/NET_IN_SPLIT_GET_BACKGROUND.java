package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 获取视频输出背景图输入参数
*/
public class NET_IN_SPLIT_GET_BACKGROUND extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    public int              dwSize;
    /**
     * 视频输出通道号
    */
    public int              nChannel;

    public NET_IN_SPLIT_GET_BACKGROUND() {
        this.dwSize = this.size();
    }
}

