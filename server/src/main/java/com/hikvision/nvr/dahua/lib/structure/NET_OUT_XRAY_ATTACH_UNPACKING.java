package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * CLIENT_XRay_AttachUnpackingResult 出参
*/
public class NET_OUT_XRAY_ATTACH_UNPACKING extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 赋值为结构体大小
    */
    public int              dwSize;

    public NET_OUT_XRAY_ATTACH_UNPACKING() {
        this.dwSize = this.size();
    }
}

