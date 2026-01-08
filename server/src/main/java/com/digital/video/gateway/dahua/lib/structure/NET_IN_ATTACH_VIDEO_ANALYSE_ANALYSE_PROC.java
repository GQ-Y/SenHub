package com.digital.video.gateway.dahua.lib.structure;


import com.digital.video.gateway.dahua.lib.NetSDKLib;
import com.sun.jna.Pointer;

/**
 * @author 260611
 * @description CLIENT_AttachVideoAnalyseAnalyseProc 输入参数
 * @date 2023/02/21 14:39:21
 */
public class NET_IN_ATTACH_VIDEO_ANALYSE_ANALYSE_PROC extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
    /**
     * 此结构体大小,必须赋值
     */
    public int              dwSize;
    /**
     * 通道号
     */
    public int              nChannelId;
    /**
     * 回调函数
     */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.fVideoAnalyseAnalyseProc cbVideoAnalyseAnalyseProc;
    /**
     * 用户信息
     */
    public Pointer          dwUser;

    public NET_IN_ATTACH_VIDEO_ANALYSE_ANALYSE_PROC() {
        this.dwSize = this.size();
    }
}

