package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * CLIENT_RemoveMediaFile接口输出参数
*/
public class NET_OUT_REMOVE_MEDIA_FILE extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 此结构体大小,必须赋值
    */
    public int              dwSize;

    public NET_OUT_REMOVE_MEDIA_FILE() {
        this.dwSize = this.size();
    }
}

