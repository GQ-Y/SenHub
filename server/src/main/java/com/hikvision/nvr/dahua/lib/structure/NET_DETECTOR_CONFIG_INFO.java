package com.hikvision.nvr.dahua.lib.structure;

import com.hikvision.nvr.dahua.lib.NetSDKLib;

/**
 * @author 251823
 * @description 检测器配置信息
 * @date 2021/09/28
 */
public class NET_DETECTOR_CONFIG_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
	/**
     *  检测器id
     */
    public int              nDetectorId;
    /**
     *  是否启用
     */
    public int              bEnable;
    /**
     *  预留字节
     */
    public byte[]           szReserved = new byte[32];
}

