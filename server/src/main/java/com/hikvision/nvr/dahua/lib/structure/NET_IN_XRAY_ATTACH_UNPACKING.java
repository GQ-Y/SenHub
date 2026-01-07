package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
import com.sun.jna.Pointer;
/**
 * CLIENT_XRay_AttachUnpackingResult 入参
*/
public class NET_IN_XRAY_ATTACH_UNPACKING extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 赋值为结构体大小
    */
    public int              dwSize;
    /**
     * 开包检查结果回调,参见回调函数定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.fXRayUnpackingResult}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.fXRayUnpackingResult cbNotify;
    /**
     * 用户信息
    */
    public Pointer          dwUser;

    public NET_IN_XRAY_ATTACH_UNPACKING() {
        this.dwSize = this.size();
    }
}

