package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * Invite事件自定义信息
*/
public class TALKING_INVITE_CUSTOM_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 开包台ip
    */
    public byte[]           szAddress = new byte[64];
    /**
     * 保留字段
    */
    public byte[]           szReserved = new byte[1024];

    public TALKING_INVITE_CUSTOM_INFO() {
    }
}

