package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * CLIENT_StartFindObjectMediaFind 接口输出参数
*/
public class NET_OUT_START_FIND_OBJECT_MEDIA_FIND extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 结构体大小
    */
    public int              dwSize;

    public NET_OUT_START_FIND_OBJECT_MEDIA_FIND() {
        this.dwSize = this.size();
    }
}

