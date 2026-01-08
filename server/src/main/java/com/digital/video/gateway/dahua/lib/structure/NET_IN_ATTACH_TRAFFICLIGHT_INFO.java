package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;

/**
 * className：NET_IN_ATTACH_TRAFFICLIGHT_INFO
 * description：CLIENT_AttachTrafficLightState 接口入参
 * author：251589
 * createTime：2021/1/19 14:30
 *
 * @version v1.0
 */
public class NET_IN_ATTACH_TRAFFICLIGHT_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
    /**
     *  结构体大小
     */
    public int              dwSize;
    /**
     *  回调函数
     */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.fTrafficLightState cbState;
    /**
     *  用户信息
     */
    public long             dwUser;

    public NET_IN_ATTACH_TRAFFICLIGHT_INFO(){
        this.dwSize = this.size();
    }
}

