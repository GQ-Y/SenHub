package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;

/**
 * @author 251823
 * @description 事件类型EVENT_IVS_INTELLI_SHELF(智能补货事件)对应的规则配置
 * @date 2022/11/15 19:52:34
 */
public class CFG_INTELLI_SHELF_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
	/**
	 * 规则名称,不同规则不能重名
	 */
    public byte[]           szRuleName = new byte[128];
	/**
	 * 规则使能
	 */
    public int              bRuleEnable;
	/**
	 * 相应物体类型个数
	 */
    public int              nObjectTypeNum;
	/**
	 * 相应物体类型列表
	 */
    public MaxNameByteArrInfo[] szObjectTypes = (MaxNameByteArrInfo[])new MaxNameByteArrInfo().toArray(com.digital.video.gateway.dahua.lib.NetSDKLib.MAX_OBJECT_LIST_SIZE);
	/**
	 * 云台预置点编号 0~65535
	 */
    public int              nPtzPresetId;
	/**
	 * 报警联动
	 */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.CFG_ALARM_MSG_HANDLE stuEventHandler = new com.digital.video.gateway.dahua.lib.NetSDKLib.CFG_ALARM_MSG_HANDLE();
	/**
	 * 事件响应时间段
	 */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.TIME_SECTION_WEEK_DAY_10[] stuTimeSection = (com.digital.video.gateway.dahua.lib.NetSDKLib.TIME_SECTION_WEEK_DAY_10[])new com.digital.video.gateway.dahua.lib.NetSDKLib.TIME_SECTION_WEEK_DAY_10().toArray(com.digital.video.gateway.dahua.lib.NetSDKLib.WEEK_DAY_NUM);
	/**
	 * 检测区顶点数
	 */
    public int              nDetectRegionPoint;
	/**
	 * 检测区
	 */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.CFG_POLYGON[] stuDetectRegion = new com.digital.video.gateway.dahua.lib.NetSDKLib.CFG_POLYGON[20];
	/**
	 * 货架上货物百分比低于阈值则报警, 0表示不报警
	 */
    public short            nThreshold;
	/**
	 * 区域ID，该通道和PresetID下的某个区域,取值范围1-5
	 */
    public short            nAreaID;
	/**
	 * 保留字节
	 */
    public byte[]           byReserved = new byte[4096];

	public CFG_INTELLI_SHELF_INFO() {
		for (int i = 0; i < stuTimeSection.length; i++) {
			stuTimeSection[i] = new com.digital.video.gateway.dahua.lib.NetSDKLib.TIME_SECTION_WEEK_DAY_10();
		}
		for (int i = 0; i < stuDetectRegion.length; i++) {
			stuDetectRegion[i] = new com.digital.video.gateway.dahua.lib.NetSDKLib.CFG_POLYGON();
		}
	}
}

