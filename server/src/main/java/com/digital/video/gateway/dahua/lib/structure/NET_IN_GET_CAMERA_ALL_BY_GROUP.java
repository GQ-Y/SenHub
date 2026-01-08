package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * CLIENT_MatrixGetCameraAllByGroup 接口输入参数
*/
public class NET_IN_GET_CAMERA_ALL_BY_GROUP extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 结构体大小
    */
    public int              dwSize;
    /**
     * 输入通道类型,参见枚举定义 {@link com.netsdk.lib.enumeration.EM_CAMERA_CHANNEL_TYPE}
    */
    public int              emChannelType;

    public NET_IN_GET_CAMERA_ALL_BY_GROUP() {
        this.dwSize = this.size();
    }
}

