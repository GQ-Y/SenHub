package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;

import static com.digital.video.gateway.dahua.lib.NetSDKLib.MAX_PATH;

/**
 * @author 47081
 * @version 1.0
 * @description 通用信息
 * @date 2021/2/22
 */
public class NET_MEDIAFILE_GENERAL_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
  /** 图片文件路径 */
    public byte[]           szFilePath = new byte[MAX_PATH];
  /** ObjectUrls的个数 */
    public int              nObjectUrlNum;
  /** 小图路径 */
    public ObjectUrl[]      szObjectUrls = (ObjectUrl[]) new ObjectUrl().toArray(8);
  /** 保留字段 */
    public byte[]           byReserved = new byte[4096];
}

