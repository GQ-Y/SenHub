package com.hikvision.nvr.dahua.lib.structure;

import com.hikvision.nvr.dahua.lib.NetSDKLib;

/**
 * @author 47081
 * @version 1.0
 * @description 设备硬盘信息
 * @date 2021/1/27
 */
public class SDK_HARDDISK_STATE extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
  /** 个数 */
    public int              dwDiskNum;
  /** 硬盘或分区信息 */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_DEV_DISKSTATE[] stDisks = (com.hikvision.nvr.dahua.lib.NetSDKLib.NET_DEV_DISKSTATE[]) new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_DEV_DISKSTATE().toArray(256);
}

