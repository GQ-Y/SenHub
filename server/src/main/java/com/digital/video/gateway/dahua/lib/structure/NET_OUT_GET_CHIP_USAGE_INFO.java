package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * CLIENT_GetChipUsageInfo 接口输出参数
*/
public class NET_OUT_GET_CHIP_USAGE_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 结构体大小
    */
    public int              dwSize;
    /**
     * 智能卡个数
    */
    public int              nChipUsageInfoNum;
    /**
     * 智能卡信息,参见结构体定义 {@link com.netsdk.lib.structure.NET_CHIP_USAGE_INFO}
    */
    public NET_CHIP_USAGE_INFO[] stuChipUsageInfo = new NET_CHIP_USAGE_INFO[64];

    public NET_OUT_GET_CHIP_USAGE_INFO() {
        this.dwSize = this.size();
        for(int i = 0; i < stuChipUsageInfo.length; i++){
            stuChipUsageInfo[i] = new NET_CHIP_USAGE_INFO();
        }
    }
}

