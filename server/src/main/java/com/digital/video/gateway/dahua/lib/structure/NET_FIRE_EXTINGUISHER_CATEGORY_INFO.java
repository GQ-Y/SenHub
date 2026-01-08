package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * @author 251823
 * @description 目标区域内物体计数信息
 * @date 2021/12/27
 */
public class NET_FIRE_EXTINGUISHER_CATEGORY_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
	/**
     *  目标物体子类型,参考{ @link EM_FIRE_EXTINGUISHER_CATEGORY_TYPE}
     */
    public int              emCategory;
    /**
     *  目标物体子类型对应计数
     */
    public int              nNum;
    /**
     *  预留字节
     */
    public byte[]           szReserved = new byte[128];
}

