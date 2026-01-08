package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
import com.sun.jna.Pointer;
/**
 * CLIENT_SetAttachFindResultHistoryCBEx 接口入参
*/
public class NET_IN_SET_ATTACH_FIND_RESULT_HISTORY_CB_EX extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 结构体大小
    */
    public int              dwSize;
    /**
     * 字节对齐
    */
    public byte[]           szReserved = new byte[4];
    /**
     * 订阅句柄
    */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.LLong  lAttachHandle;
    /**
     * 回调函数,参见回调函数定义 {@link com.netsdk.lib.com.digital.video.gateway.dahua.lib.NetSDKLib.fResultOfHumanHistoryEx}
    */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.fResultOfHumanHistoryEx fCBEx;
    /**
     * 用户数据
    */
    public Pointer          dwUserEx;

    public NET_IN_SET_ATTACH_FIND_RESULT_HISTORY_CB_EX() {
        this.dwSize = this.size();
    }
}

