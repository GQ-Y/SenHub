package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * CLIENT_StopFindObjectMediaFind 接口输入参数
*/
public class NET_IN_STOP_FIND_OBJECT_MEDIA_FIND extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 此结构体大小,必须赋值
    */
    public int              dwSize;

    public NET_IN_STOP_FIND_OBJECT_MEDIA_FIND() {
        this.dwSize = this.size();
    }
}

