package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * EM_PRC_NETAPP_TYPE_GET_DIAL_INFO 入参
*/
public class NET_IN_NETAPP_GET_DIAL_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    public int              dwSize;
    /**
     * 网卡名称,参见枚举定义 {@link com.netsdk.lib.enumeration.EM_DIAL_INTERFACE}
    */
    public int              emName;

    public NET_IN_NETAPP_GET_DIAL_INFO() {
        this.dwSize = this.size();
    }
}

