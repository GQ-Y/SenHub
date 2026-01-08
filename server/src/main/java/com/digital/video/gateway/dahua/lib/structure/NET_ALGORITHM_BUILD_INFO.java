package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;

/**
 * @author 47081
 * @version 1.0
 * @description 算法构建信息
 * @date 2021/2/20
 */
public class NET_ALGORITHM_BUILD_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
    public NET_ALGORITHM_SVN_INFO stuSvnInfo;                     // 算法SVN信息
  /** 保留字节 */
    public byte[]           byReserved = new byte[1024];
}

