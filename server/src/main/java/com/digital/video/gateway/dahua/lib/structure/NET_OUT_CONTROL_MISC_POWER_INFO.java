package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * CLIENT_ControlMiscPower 输出参数
*/
public class NET_OUT_CONTROL_MISC_POWER_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 此结构体大小,必须赋值
    */
    public int              dwSize;

    public NET_OUT_CONTROL_MISC_POWER_INFO() {
        this.dwSize = this.size();
    }
}

