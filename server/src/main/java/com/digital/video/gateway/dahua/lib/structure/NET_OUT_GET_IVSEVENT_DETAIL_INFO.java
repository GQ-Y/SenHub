package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * CLIENT_GetIVSEventDetail接口出参
*/
public class NET_OUT_GET_IVSEVENT_DETAIL_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 此结构体大小,必须赋值
    */
    public int              dwSize;

    public NET_OUT_GET_IVSEVENT_DETAIL_INFO() {
        this.dwSize = this.size();
    }
}

