package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 导出数据包描述
*/
public class NET_STREAM_DATA_FILE_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 目录名：导出数据存放文件夹
    */
    public byte[]           szDestFile = new byte[70];
    /**
     * 预留字节1
    */
    public byte[]           szResvered1 = new byte[2];
    /**
     * 导出数据存放路径
    */
    public byte[]           szDestPath = new byte[128];
    /**
     * 导出文件名
    */
    public byte[]           szDestFileName = new byte[128];
    /**
     * 文件分片索引号从1开始一次递增, 若单个文件完整输出则填0
    */
    public int              nIndex;
    /**
     * 预留字节
    */
    public byte[]           szResvered = new byte[1024];

    public NET_STREAM_DATA_FILE_INFO() {
    }
}

