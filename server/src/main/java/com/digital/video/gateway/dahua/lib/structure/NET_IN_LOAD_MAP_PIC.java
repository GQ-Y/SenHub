package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * CLIENT_LoadMapPic 接口输入参数
*/
public class NET_IN_LOAD_MAP_PIC extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 此结构体大小,必须赋值
    */
    public int              dwSize;

    public NET_IN_LOAD_MAP_PIC() {
        this.dwSize = this.size();
    }
}

