package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 关联通道信息
*/
public class NET_RELATED_CHANNEL_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 进包口通道号, -1表示不存在此字段
    */
    public int              nInChannel;
    /**
     * 出包口通道号, -1表示不存在此字段
    */
    public int              nOutChannel;
    /**
     * 预留字节
    */
    public byte[]           szReserved = new byte[32];

    public NET_RELATED_CHANNEL_INFO() {
    }
}

