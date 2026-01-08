package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * CLIENT_GetWorkGroupDeviceInfos 接口输入参数
*/
public class NET_IN_GET_WORK_GROUP_DEVICE_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 结构体大小
    */
    public int              dwSize;
    /**
     * 工作组名称, 用于获取工作组实例
    */
    public byte[]           szName = new byte[32];

    public NET_IN_GET_WORK_GROUP_DEVICE_INFO() {
        this.dwSize = this.size();
    }
}

