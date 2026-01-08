package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;
import com.digital.video.gateway.dahua.lib.structure.NET_PHASES_INFO;

/**
 * @author 251823
 * @description 栅栏列表信息
 * @date 2021/09/02
 */
public class NET_BARRIER_LIST_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
	/**
     *  栅栏编号
     */
    public int              nBarrierNo;
    /**
     *  相位列表个数
     */
    public int              nPhaseNum;
    /**
     *  相位列表
     */
    public NET_PHASES_INFO[] stuPhaseInfo = (NET_PHASES_INFO[]) new NET_PHASES_INFO().toArray(16);
    /**
     *  预留字节
     */
    public byte[]           szReserved = new byte[32];
}

