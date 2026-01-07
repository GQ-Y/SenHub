package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
import com.sun.jna.Pointer;
/**
 * 设置视频输出背景图输入参数
*/
public class NET_IN_SPLIT_SET_BACKGROUND extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    public int              dwSize;
    /**
     * 视频输出通道号
    */
    public int              nChannel;
    /**
     * 使能
    */
    public int              bEnable;
    /**
     * 背景图名称
    */
    public Pointer          pszFileName;

    public NET_IN_SPLIT_SET_BACKGROUND() {
        this.dwSize = this.size();
    }
}

