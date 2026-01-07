package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * CLIENT_GetXRaySourceCaps 接口输入参数
*/
public class NET_IN_GET_XRAY_SOURCE_CAPS_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 此结构体大小,必须赋值
    */
    public int              dwSize;

    public NET_IN_GET_XRAY_SOURCE_CAPS_INFO() {
        this.dwSize = this.size();
    }
}

