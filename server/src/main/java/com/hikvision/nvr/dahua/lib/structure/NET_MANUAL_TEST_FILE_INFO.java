package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * NET_MANUAL_TEST_FILE_INFO 文件信息
*/
public class NET_MANUAL_TEST_FILE_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 图片数量
    */
    public int              nPictureCount;
    /**
     * 视频数量
    */
    public int              nVideoCount;
    /**
     * 预留字节
    */
    public byte[]           szReserved = new byte[32];

    public NET_MANUAL_TEST_FILE_INFO() {
    }
}

