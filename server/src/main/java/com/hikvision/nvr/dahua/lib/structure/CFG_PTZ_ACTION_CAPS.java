package com.hikvision.nvr.dahua.lib.structure;/**
 * @author 47081
 * @descriptio
 * @date 2020/11/9
 * @version 1.0
 */

import com.hikvision.nvr.dahua.lib.NetSDKLib;

import java.util.Arrays;

/**
 * @author 47081
 * @version 1.0
 * @description 支持的云台动作类型
 * @date 2020/11/9
 */
public class CFG_PTZ_ACTION_CAPS extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
    /**
     * 是否支持水平移动
     */
    public int              bSupportPan;
    /**
     * 是否支持垂直移动
     */
    public int              bSupportTile;
    /**
     * 是否支持变倍
     */
    public int              bSupportZoom;
    /**
     * 预留
     */
    public byte[]           byReserved = new byte[116];

    @Override
    public String toString() {
        return "CFG_PTZ_ACTION_CAPS{" +
                "bSupportPan=" + bSupportPan +
                ", bSupportTile=" + bSupportTile +
                ", bSupportZoom=" + bSupportZoom +
                '}';
    }
}

