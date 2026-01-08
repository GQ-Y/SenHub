package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;

/**
 * @author 251823
 * @description 场景组合
 * @date 2021/01/11
 */
public class CFG_SUPPORTED_COMP extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
	/**
	 * 场景组合项下支持的场景个数
	 */
    public int              nSupportedData;
	/**
	 * 场景组合项下支持的场景列表
	 */
    public StringByteArrSixteen[] szSupportedData = (StringByteArrSixteen[])new StringByteArrSixteen().toArray(com.digital.video.gateway.dahua.lib.NetSDKLib.MAX_SUPPORTED_COMP_DATA);

	 public CFG_SUPPORTED_COMP() {
		 for (int i = 0; i < szSupportedData.length; i++) {
			 szSupportedData[i] = new StringByteArrSixteen();
			}
	 }
}

