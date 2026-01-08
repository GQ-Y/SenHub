package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
import com.sun.jna.Pointer;
/**
 * CLIENT_AttachRecordManagerState 入参
*/
public class NET_IN_RECORDMANAGER_ATTACH_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 结构体大小
    */
    public int              dwSize;
    /**
     * 回调函数,参见回调函数定义 {@link com.netsdk.lib.com.digital.video.gateway.dahua.lib.NetSDKLib.fRecordManagerStateCallBack}
    */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.fRecordManagerStateCallBack cbNotify;
    /**
     * 用户信息
    */
    public Pointer          dwUser;

    public NET_IN_RECORDMANAGER_ATTACH_INFO() {
        this.dwSize = this.size();
    }
}

