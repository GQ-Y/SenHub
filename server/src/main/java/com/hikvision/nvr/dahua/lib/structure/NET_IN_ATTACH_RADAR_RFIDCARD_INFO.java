package com.hikvision.nvr.dahua.lib.structure;


import com.hikvision.nvr.dahua.lib.NetSDKLib;
import com.sun.jna.Pointer;

/**
 * @author ： 260611
 * @description ： CLIENT_AttachRadarRFIDCardInfo接口入参
 * @since ： Created in 2022/02/11 10:22
 */

public class NET_IN_ATTACH_RADAR_RFIDCARD_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
    /**
     * 结构体大小
     */
    public int              dwSize;
    /**
     * 通道号
     */
    public int              nChannelID;
    /**
     * 雷达RFIDCard信息回调
     */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.fRadarRFIDCardInfoCallBack cbRFIDCardInfo;
    /**
     * 用户数据
     */
    public Pointer          dwUser;

    public NET_IN_ATTACH_RADAR_RFIDCARD_INFO() {
        this.dwSize = this.size();
    }
}

