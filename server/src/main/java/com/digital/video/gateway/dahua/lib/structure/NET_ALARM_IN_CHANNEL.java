package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;

/**
 * @author 251823
 * @description 报警输入通道信息
 * @date 2021/11/04
 */
public class NET_ALARM_IN_CHANNEL extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
	/**
     * 结构体大小
     */
    public int              dwSize;
    /**
     *  是否有效, FALSE表示该报警通道没有使用
     */
    public int              bValid;
    /**
     *  报警通道号
     */
    public int              nChannel;
    /**
     *  设备ID, 本地报警通道: "Local", 远程设备: 使用uuid表示
     */
    public byte[]           szDeviceID = new byte[com.digital.video.gateway.dahua.lib.NetSDKLib.NET_DEV_ID_LEN];
    /**
     *  报警通道名称
     */
    public byte[]           szName = new byte[com.digital.video.gateway.dahua.lib.NetSDKLib.NET_DEV_NAME_LEN];

    public NET_ALARM_IN_CHANNEL(){
        this.dwSize = this.size();
    }
}

