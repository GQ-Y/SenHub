package com.hikvision.nvr.dahua.lib.structure;

import com.hikvision.nvr.dahua.lib.NetSDKLib;
import com.hikvision.nvr.dahua.lib.structure.NET_PHASES_INFO;

/**
 * @author 251823
 * @description 栅栏列表信息
 * @date 2021/09/02
 */
public class NET_BARRIER_LIST_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
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

