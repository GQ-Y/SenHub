package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 文件信息
*/
public class NET_XRAY_DOWNLOAD_RECORD_FILEINFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 路径
    */
    public byte[]           szPath = new byte[128];
    /**
     * 大小，单位字节
    */
    public long             nSize;
    /**
     * 预留字节
    */
    public byte[]           szReserved = new byte[1024];

    public NET_XRAY_DOWNLOAD_RECORD_FILEINFO() {
    }
}

