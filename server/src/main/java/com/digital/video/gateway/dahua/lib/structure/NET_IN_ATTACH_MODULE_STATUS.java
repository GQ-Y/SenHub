package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
import com.sun.jna.Pointer;
/**
 * CLIENT_AttachModuleStatus 接口入参
*/
public class NET_IN_ATTACH_MODULE_STATUS extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 此结构体大小,必须赋值
    */
    public int              dwSize;
    /**
     * 字节对齐
    */
    public byte[]           szAlign = new byte[4];
    /**
     * 回调函数,参见回调函数定义 {@link com.netsdk.lib.com.digital.video.gateway.dahua.lib.NetSDKLib.fNotifyModuleStatus}
    */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.fNotifyModuleStatus cbNotify;
    /**
     * 用户自定义参数
    */
    public Pointer          dwUser;

    public NET_IN_ATTACH_MODULE_STATUS() {
        this.dwSize = this.size();
    }
}

