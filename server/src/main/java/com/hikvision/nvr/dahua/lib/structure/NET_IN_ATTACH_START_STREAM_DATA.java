package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
import com.sun.jna.Pointer;
/**
 * CLIENT_AttachStartStreamData 接口输入参数
*/
public class NET_IN_ATTACH_START_STREAM_DATA extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 此结构体大小,必须赋值
    */
    public int              dwSize;
    /**
     * 回调函数,参见回调函数定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.fStartStreamDataCallBack}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.fStartStreamDataCallBack cbStartStreamData;
    /**
     * 用户信息
    */
    public Pointer          dwUser;

    public NET_IN_ATTACH_START_STREAM_DATA() {
        this.dwSize = this.size();
    }
}

