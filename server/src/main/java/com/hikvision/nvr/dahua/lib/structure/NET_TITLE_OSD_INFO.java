package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * OSD使能状态及文本信息
*/
public class NET_TITLE_OSD_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 叠加内容
    */
    public byte[]           szText = new byte[1024];
    /**
     * 是否使能osd显示
    */
    public int              nEnable;
    /**
     * 预留字节
    */
    public byte[]           szReserved = new byte[1020];

    public NET_TITLE_OSD_INFO() {
    }
}

