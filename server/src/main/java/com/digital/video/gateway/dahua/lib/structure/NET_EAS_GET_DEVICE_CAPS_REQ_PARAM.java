package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 获取EAS设备能力集查询参数
*/
public class NET_EAS_GET_DEVICE_CAPS_REQ_PARAM extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * EAS通道号，-1表示所有通道
    */
    public int              nDeviceChannel;
    /**
     * 保留字节
    */
    public byte[]           szResvered = new byte[128];

    public NET_EAS_GET_DEVICE_CAPS_REQ_PARAM() {
    }
}

