package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * CLIENT_QueryDevInfo , NET_QUERY_WORKGROUP_NAMES 命令输入参数
*/
public class NET_IN_WORKGROUP_NAMES extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    public int              dwSize;

    public NET_IN_WORKGROUP_NAMES() {
        this.dwSize = this.size();
    }
}

