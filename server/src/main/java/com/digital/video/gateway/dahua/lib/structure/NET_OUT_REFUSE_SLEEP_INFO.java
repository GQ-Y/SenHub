package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * CLIENT_RefuseLowPowerDevSleep 接口输出参数
*/
public class NET_OUT_REFUSE_SLEEP_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 结构体大小
    */
    public int              dwSize;

    public NET_OUT_REFUSE_SLEEP_INFO() {
        this.dwSize = this.size();
    }
}

