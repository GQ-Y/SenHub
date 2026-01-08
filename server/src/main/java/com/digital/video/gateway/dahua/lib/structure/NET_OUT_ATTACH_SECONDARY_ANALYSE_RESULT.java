package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 订阅二次录像分析实时结果输出参数
*/
public class NET_OUT_ATTACH_SECONDARY_ANALYSE_RESULT extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 赋值为结构体大小
    */
    public int              dwSize;

    public NET_OUT_ATTACH_SECONDARY_ANALYSE_RESULT() {
        this.dwSize = this.size();
    }
}

