package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 历史库人体图片信息
*/
public class NET_HISTORY_HUMAN_IMAGE_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 图片大小,单位:字节
    */
    public int              nLength;
    /**
     * 图片宽度
    */
    public int              nWidth;
    /**
     * 图片高度
    */
    public int              nHeight;
    /**
     * 文件路径
    */
    public byte[]           szFilePath = new byte[260];

    public NET_HISTORY_HUMAN_IMAGE_INFO() {
    }
}

