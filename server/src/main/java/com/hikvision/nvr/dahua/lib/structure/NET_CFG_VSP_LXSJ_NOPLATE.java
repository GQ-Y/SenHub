 package com.hikvision.nvr.dahua.lib.structure;


 import com.hikvision.nvr.dahua.lib.NetSDKLib;

public class NET_CFG_VSP_LXSJ_NOPLATE extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
/** 使能*/
    public			int            bEnable;
/** 无牌车内容*/
    public			byte[]         szText = new byte[128];
/** 预留*/
    public			byte[]         byReserved = new byte[380];
}

