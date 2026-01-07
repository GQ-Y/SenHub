package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 访客登录业务能力集
*/
public class NET_VISITOR_REGISTRATION_CAPS extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 访客业务是否支持超长的访客信息（相应地，访客名称、地址、联系方式等字段长度扩大）
    */
    public int              bSupportSuperLongVisitorInfo;
    /**
     * 保留字节
    */
    public byte[]           szReserved = new byte[252];

    public NET_VISITOR_REGISTRATION_CAPS() {
    }
}

