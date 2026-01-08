package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
import com.sun.jna.Pointer;
/**
 * CLIENT_AttachAreaFlow 输入参数
*/
public class NET_IN_ATTACH_AREA_FLOW extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 此结构体大小,必须赋值
    */
    public int              dwSize;
    /**
     * 通道号 -1 表示所有通道
    */
    public int              nChannelID;
    /**
     * 回调函数,参见回调函数定义 {@link com.netsdk.lib.com.digital.video.gateway.dahua.lib.NetSDKLib.fNotifyAreaFlowInfo}
    */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.fNotifyAreaFlowInfo cbNotifyAreaFlowInfo;
    /**
     * 用户信息
    */
    public Pointer          dwUser;

    public NET_IN_ATTACH_AREA_FLOW() {
        this.dwSize = this.size();
    }
}

