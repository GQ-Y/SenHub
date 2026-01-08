package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;

import static com.digital.video.gateway.dahua.lib.NetSDKLib.NET_DEVICE_NAME_LEN;

/**
 * className：NET_FAN_INFO
 * description：风扇信息
 * author：251589
 * createTime：2021/2/25 13:40
 *
 * @version v1.0
 */

public class NET_FAN_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
    /**
     * dwSize;
     */
    public int              dwSize;
    /**
     *  名称
     */
    public byte[]           szName = new byte[NET_DEVICE_NAME_LEN];
    /**
     *  速度
     */
    public int              nSpeed;

    public NET_FAN_INFO(){
        this.dwSize = this.size();
    }
}

