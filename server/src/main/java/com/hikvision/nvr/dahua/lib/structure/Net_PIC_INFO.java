package com.hikvision.nvr.dahua.lib.structure;

import com.hikvision.nvr.dahua.lib.NetSDKLib;

import static com.hikvision.nvr.dahua.lib.NetSDKLib.MAX_PATH;

/**
 * className：Net_PIC_INFO
 * description：图片文件信息
 * author：251589
 * createTime：2020/12/28 10:57
 *
 * @version v1.0
 */
public class Net_PIC_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
    public int              dwFileLenth;                          // 文件大小, 单位:字节
    public byte[]           szFilePath = new byte[MAX_PATH];      // 文件路径
    public byte[]           bReserved = new byte[256];            // 保留字段
}

