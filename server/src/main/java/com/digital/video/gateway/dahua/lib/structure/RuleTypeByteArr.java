package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;

/**
 * @author 251823
 * @description 规则类型字节数组
 * @date 2021/09/27
 */
public class RuleTypeByteArr extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
	/**
	 * 单个规则类型
	 */
    public byte[]           szRuleTypeByteArr = new byte[32];
}

