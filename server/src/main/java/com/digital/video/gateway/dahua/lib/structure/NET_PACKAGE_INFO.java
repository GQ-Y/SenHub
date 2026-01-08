package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;


/**
 * 危险等级
 * 
 * @author ： 260611
 * @since ： Created in 2021/10/19 14:46
 */
public class NET_PACKAGE_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
    /**
     *  包裹危险等级, 一个包裹内有多个危险等级显示最高危等级
     */
    public int              emDangerGrade;
    /**
     *  保留字节,留待扩展
     */
    public byte             byReserved[] = new byte[128];
}

