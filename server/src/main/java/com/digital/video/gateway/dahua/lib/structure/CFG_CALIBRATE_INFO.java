package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;

/**
 * @author 251823
 * @description 标定基本信息
 * @date 2021/02/02
 */
public class CFG_CALIBRATE_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
	 /**
	  * 标定信息单元二维数组
	  */
    public CFG_CALIBRATE_UNIT_INFO_ARR[] stuCalibrateUnitInfo = new CFG_CALIBRATE_UNIT_INFO_ARR[5];
	/**
	 * 标定数量
	 */
    public int              nInfoNum;

	public CFG_CALIBRATE_INFO() {
		for (int i = 0; i < stuCalibrateUnitInfo.length; i++) {
			stuCalibrateUnitInfo[i] = new CFG_CALIBRATE_UNIT_INFO_ARR();
		}
	}	
}

