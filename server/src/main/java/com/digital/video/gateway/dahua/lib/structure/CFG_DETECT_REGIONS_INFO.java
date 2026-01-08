package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;

/**
 * @author 47081
 * @version 1.0
 * @description 规则相关检测区域信息
 * @date 2021/2/22
 */
public class CFG_DETECT_REGIONS_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
	/** 检测区域使能字段不存在时默认为TRUE */
    public int              bEnable;
	/**
	 * 检测区域顶点数
	 */
    public int              nDetectRegionPoint;
	/**
	 * 检测区域
	 */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.CFG_POLYGON[] stuDetectRegion = new com.digital.video.gateway.dahua.lib.NetSDKLib.CFG_POLYGON[20];

	public CFG_DETECT_REGIONS_INFO() {
		for (int i = 0; i < stuDetectRegion.length; i++) {
			stuDetectRegion[i] = new com.digital.video.gateway.dahua.lib.NetSDKLib.CFG_POLYGON();
		}
	}
}

