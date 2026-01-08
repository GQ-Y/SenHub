package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * CLIENT_RebootDevice 接口输入参数
*/
public class NET_IN_REBOOT_DEVICE extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 结构体大小
    */
    public int              dwSize;

    public NET_IN_REBOOT_DEVICE() {
        this.dwSize = this.size();
    }
}

