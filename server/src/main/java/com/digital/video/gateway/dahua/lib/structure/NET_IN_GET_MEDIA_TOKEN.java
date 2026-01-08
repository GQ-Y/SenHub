package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * CLIENT_GetMediaToken接口输入参数
*/
public class NET_IN_GET_MEDIA_TOKEN extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 此结构体大小,必须赋值
    */
    public int              dwSize;
    /**
     * token有效时间，单位：秒 范围5-30s。  当输入<5按照5处理，当输入>30, 按照30处理
    */
    public int              nKeepAliveTime;
    /**
     * 请求token的客户端IP
    */
    public byte[]           szClientIP = new byte[48];

    public NET_IN_GET_MEDIA_TOKEN() {
        this.dwSize = this.size();
    }
}

