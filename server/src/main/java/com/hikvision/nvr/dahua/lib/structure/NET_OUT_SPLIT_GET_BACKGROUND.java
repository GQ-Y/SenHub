package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 获取视频输出背景图输出参数
*/
public class NET_OUT_SPLIT_GET_BACKGROUND extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    public int              dwSize;
    /**
     * 使能
    */
    public int              bEnable;
    /**
     * 背景图名称
    */
    public byte[]           szFileName = new byte[256];

    public NET_OUT_SPLIT_GET_BACKGROUND() {
        this.dwSize = this.size();
    }
}

