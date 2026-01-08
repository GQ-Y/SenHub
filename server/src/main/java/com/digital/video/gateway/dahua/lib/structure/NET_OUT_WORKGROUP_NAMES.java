package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * CLIENT_QueryDevInfo , NET_QUERY_WORKGROUP_NAMES 命令输出参数
*/
public class NET_OUT_WORKGROUP_NAMES extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    public int              dwSize;
    /**
     * 工作组个数
    */
    public int              nCount;
    /**
     * 每个工作组的名字
    */
    public BYTE_ARRAY_32[]  szName = new BYTE_ARRAY_32[64];

    public NET_OUT_WORKGROUP_NAMES() {
        this.dwSize = this.size();
        for(int i = 0; i < szName.length; i++){
            szName[i] = new BYTE_ARRAY_32();
        }
    }
}

