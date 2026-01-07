package com.hikvision.nvr.dahua.lib.structure;

import com.hikvision.nvr.dahua.lib.NetSDKLib;
import com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure;
/**
 * @author 119178
 * @description 通道相关信息
 * @date 2021/4/21
 */
public class CFG_VSP_GAYS_CHANNEL_INFO extends SdkStructure {
    public byte[]           szId = new byte[com.hikvision.nvr.dahua.lib.NetSDKLib.CFG_COMMON_STRING_64]; // 通道编号	字符串（24位）
    public int              nAlarmLevel;                          // 报警级别[1,6]	整型
}

