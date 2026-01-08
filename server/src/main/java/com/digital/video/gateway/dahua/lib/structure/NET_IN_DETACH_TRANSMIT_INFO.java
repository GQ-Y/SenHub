package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;
import com.sun.jna.Pointer;

/**
 * @author 251823
 * @version 1.0
 * @description CLIENT_DetachTransmitInfo输入参数
 * @date 2022/02/14
 */
public class NET_IN_DETACH_TRANSMIT_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
    /**
     *  用户使用该结构体时，dwSize需赋值为sizeof(NET_IN_DETACH_TRANSMIT_INFO)
     */
    public int              dwSize;
    /**
     *  Json请求数据,用户申请空间
     */
    public Pointer          szInJsonBuffer;
    /**
     *  Json请求数据长度
     */
    public int              dwInJsonBufferSize;

    public NET_IN_DETACH_TRANSMIT_INFO(){
        this.dwSize = this.size();
    }
}

