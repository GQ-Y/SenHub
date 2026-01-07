package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
import com.sun.jna.Pointer;
/**
 * CLIENT_AttachIotboxComm 接口入参
*/
public class NET_IN_ATTACH_IOTBOX_COMM_EX extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 此结构体大小,必须赋值
    */
    public int              dwSize;
    /**
     * 用户自定义参数
    */
    public Pointer          dwUser;
    /**
     * 回调函数,参见回调函数定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.fNotifyIotboxRealdataEx}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.fNotifyIotboxRealdataEx cbNotifyIotboxRealdataEx;

    public NET_IN_ATTACH_IOTBOX_COMM_EX() {
        this.dwSize = this.size();
    }
}

