package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 获取SIM卡的状态类型出参
*/
public class NET_OUT_SIMINFO_GET_SIMSTATE extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 赋值为结构体大小
    */
    public int              dwSize;
    /**
     * SIM卡状态类型,参见枚举定义 {@link com.netsdk.lib.enumeration.EM_SIMSTATE_MODE}
    */
    public int              emMode;

    public NET_OUT_SIMINFO_GET_SIMSTATE() {
        this.dwSize = this.size();
    }
}

