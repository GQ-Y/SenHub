package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
import com.sun.jna.Pointer;
/**
 * CLIENT_AttachSniffer 接口入参
*/
public class NET_IN_ATTACH_SNIFFER extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    public int              dwSize;
    /**
     * 回调函数,参见回调函数定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.fAttachSniffer}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.fAttachSniffer cbSniffer;
    /**
     * 用户信息
    */
    public Pointer          dwUser;

    public NET_IN_ATTACH_SNIFFER() {
        this.dwSize = this.size();
    }
}

