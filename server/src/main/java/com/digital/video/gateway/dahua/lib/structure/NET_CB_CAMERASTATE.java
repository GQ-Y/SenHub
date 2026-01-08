package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;

/**
 * 设备状态回调结果
 * {@link com.digital.video.gateway.dahua.lib.NetSDKLib.fCameraStateCallBack}
 *
 * @author ： 47040
 * @since ： Created in 2021/1/15 14:14
 */
public class NET_CB_CAMERASTATE extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
    /**
     * 结构体大小
     */
    public int              dwSize;
    /**
     * 所在通道
     */
    public int              nChannel;
    /**
     * 连接状态
     * {@link com.digital.video.gateway.dahua.lib.NetSDKLib.CONNECT_STATE emConnectState}
     */
    public int              emConnectState;

    public NET_CB_CAMERASTATE() {
        this.dwSize = this.size();
    }
}

