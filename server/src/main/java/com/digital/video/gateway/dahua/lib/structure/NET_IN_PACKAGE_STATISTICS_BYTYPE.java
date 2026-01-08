package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * @author 291189
 * @version 1.0
 * @description   按危险类型统计的信息
 * @date 2021/7/1
 */
public class NET_IN_PACKAGE_STATISTICS_BYTYPE extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
    /**
     *  危险物等级,参考枚举{@link com.netsdk.lib.com.digital.video.gateway.dahua.lib.NetSDKLib.EM_INSIDE_OBJECT_TYPE }
     */
    public int              emType;                               // 危险物类型
    public int              nCount;                               // 危险物数量
    public byte[]           byReserved = new byte[64];            // 预留字段
}

