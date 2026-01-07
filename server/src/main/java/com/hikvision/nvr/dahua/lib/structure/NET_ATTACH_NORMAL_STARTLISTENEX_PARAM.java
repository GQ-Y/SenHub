package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
import com.sun.jna.Pointer;
/**
 * EM_STARTLISTENEX对应的订阅参数
*/
public class NET_ATTACH_NORMAL_STARTLISTENEX_PARAM extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 回调函数,参见回调函数定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.fAttachNormalCallBack}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.fAttachNormalCallBack cbAttachNormal;
    /**
     * 用户信息
    */
    public Pointer          dwUser;
    /**
     * 预留字节
    */
    public byte[]           szReserved = new byte[1024];

    public NET_ATTACH_NORMAL_STARTLISTENEX_PARAM() {
    }
}

