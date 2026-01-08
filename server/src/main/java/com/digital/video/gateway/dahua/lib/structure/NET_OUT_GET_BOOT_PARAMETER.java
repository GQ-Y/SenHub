package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * CLIENT_GetBootParameter 出参
*/
public class NET_OUT_GET_BOOT_PARAMETER extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 结构体大小
    */
    public int              dwSize;
    /**
     * 实际返回的参数个数
    */
    public int              nRetBootParamNum;
    /**
     * 具体参数信息,参见结构体定义 {@link com.netsdk.lib.structure.NET_BOOT_PARAMETER_INFO}
    */
    public NET_BOOT_PARAMETER_INFO[] stuBootParamInfo = new NET_BOOT_PARAMETER_INFO[32];

    public NET_OUT_GET_BOOT_PARAMETER() {
        this.dwSize = this.size();
        for(int i = 0; i < stuBootParamInfo.length; i++){
            stuBootParamInfo[i] = new NET_BOOT_PARAMETER_INFO();
        }
    }
}

