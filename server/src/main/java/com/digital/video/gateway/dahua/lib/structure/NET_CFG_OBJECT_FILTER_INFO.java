package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * @author 251823
 * @description 物体过滤器
 * @date 2021/07/06
 */
public class NET_CFG_OBJECT_FILTER_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
	 /**
     *  物体过滤类型个数
     */
    public int              nObjectFilterTypeNum;
    /**
     *  物体过滤类型,参考{@link EM_CFG_OBJECT_FILTER_TYPE }
     */
    public int[]            emObjectFilterType = new int[16];
    /**
     *  保留字节
     */
    public byte[]           byReserved = new byte[1024];
}

