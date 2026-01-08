package com.digital.video.gateway.dahua.lib.structure;


import com.digital.video.gateway.dahua.lib.NetSDKLib;

/** 
* @author 291189
* @description  金属锈蚀结果 
* @date 2022/06/28 19:44:57
*/
public class NET_METAL_CORROSION extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
/** 
包围盒
*/
    public NET_RECT         stuBoundingBox = new NET_RECT();
    /**
     * 目标置信度，0~100，越大表示越高
    */
    public int              nConfidence;
    /**
     * 金属锈蚀状态,参见枚举定义 {@link com.netsdk.lib.enumeration.EM_METAL_CORROSION_STATE}
    */
    public int              emMetalState;
/** 
预留字段
*/
    public			byte[]         bReserved = new byte[120];

public			NET_METAL_CORROSION(){
}
}

