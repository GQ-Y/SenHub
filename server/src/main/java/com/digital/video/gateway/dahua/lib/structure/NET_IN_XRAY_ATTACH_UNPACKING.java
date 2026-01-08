package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
import com.sun.jna.Pointer;
/**
 * CLIENT_XRay_AttachUnpackingResult 入参
*/
public class NET_IN_XRAY_ATTACH_UNPACKING extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 赋值为结构体大小
    */
    public int              dwSize;
    /**
     * 开包检查结果回调,参见回调函数定义 {@link com.netsdk.lib.com.digital.video.gateway.dahua.lib.NetSDKLib.fXRayUnpackingResult}
    */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.fXRayUnpackingResult cbNotify;
    /**
     * 用户信息
    */
    public Pointer          dwUser;

    public NET_IN_XRAY_ATTACH_UNPACKING() {
        this.dwSize = this.size();
    }
}

