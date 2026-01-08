package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
import com.sun.jna.Pointer;
/**
 * CLIENT_AttachAISProc 输入参数
*/
public class NET_IN_ATTACH_AIS_PROC extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 此结构体大小,必须赋值
    */
    public int              dwSize;
    /**
     * 回调函数,参见回调函数定义 {@link com.netsdk.lib.com.digital.video.gateway.dahua.lib.NetSDKLib.fNotifyAISProc}
    */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.fNotifyAISProc cbNotify;
    /**
     * 用户信息
    */
    public Pointer          dwUser;

    public NET_IN_ATTACH_AIS_PROC() {
        this.dwSize = this.size();
    }
}

