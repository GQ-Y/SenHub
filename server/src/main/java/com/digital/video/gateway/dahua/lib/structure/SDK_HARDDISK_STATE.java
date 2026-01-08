package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;

/**
 * @author 47081
 * @version 1.0
 * @description 设备硬盘信息
 * @date 2021/1/27
 */
public class SDK_HARDDISK_STATE extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
  /** 个数 */
    public int              dwDiskNum;
  /** 硬盘或分区信息 */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.NET_DEV_DISKSTATE[] stDisks = (com.digital.video.gateway.dahua.lib.NetSDKLib.NET_DEV_DISKSTATE[]) new com.digital.video.gateway.dahua.lib.NetSDKLib.NET_DEV_DISKSTATE().toArray(256);
}

