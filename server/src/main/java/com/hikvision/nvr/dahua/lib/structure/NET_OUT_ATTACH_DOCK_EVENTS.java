package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * CLIENT_AttachDockEvents 接口输出参数
*/
public class NET_OUT_ATTACH_DOCK_EVENTS extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 结构体大小
    */
    public int              dwSize;

    public NET_OUT_ATTACH_DOCK_EVENTS() {
        this.dwSize = this.size();
    }
}

