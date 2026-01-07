package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 智能球机控制输出参数
*/
public class NET_OUT_CONTROL_INTELLITRACKER extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    public int              dwSize;

    public NET_OUT_CONTROL_INTELLITRACKER() {
        this.dwSize = this.size();
    }
}

