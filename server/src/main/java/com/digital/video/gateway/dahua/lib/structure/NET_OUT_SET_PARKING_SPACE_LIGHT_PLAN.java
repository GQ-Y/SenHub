package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 设置车位指示灯亮灯计划出参
*/
public class NET_OUT_SET_PARKING_SPACE_LIGHT_PLAN extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 结构体大小
    */
    public int              dwSize;

    public NET_OUT_SET_PARKING_SPACE_LIGHT_PLAN() {
        this.dwSize = this.size();
    }
}

