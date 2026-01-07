package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * CLIENT_AttachStartStreamData 接口输出参数
*/
public class NET_OUT_ATTACH_START_STREAM_DATA extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 结构体大小
    */
    public int              dwSize;

    public NET_OUT_ATTACH_START_STREAM_DATA() {
        this.dwSize = this.size();
    }
}

