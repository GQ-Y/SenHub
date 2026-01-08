package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;
import com.digital.video.gateway.dahua.lib.callback.fVideoStatHeatMapCallBack;
import com.sun.jna.Pointer;

/**
 * @author 47081
 * @version 1.0
 * @description 订阅热度图信息入参
 * @date 2020/9/21
 */
public class NET_IN_ATTACH_VIDEOSTAT_HEATMAP extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
    /**
     * 此结构体大小
     */
    public int              dwSize;
    /**
     * 视频通道号
     */
    public int              nChannel;
    /**
     * 热图数据回调
     */
    public fVideoStatHeatMapCallBack cbVideoStatHeatMap;
    /**
     * 用户数据
     */
    public Pointer          dwUser;

    public NET_IN_ATTACH_VIDEOSTAT_HEATMAP(){
        this.dwSize=size();
    }
}

