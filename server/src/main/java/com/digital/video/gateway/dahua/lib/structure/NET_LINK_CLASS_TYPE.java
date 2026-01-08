package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 联动业务大类选项
*/
public class NET_LINK_CLASS_TYPE extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 待级联的业务所在通道号
    */
    public int              nChannel;
    /**
     * 待级联的业务大类,参见枚举定义 {@link com.netsdk.lib.com.digital.video.gateway.dahua.lib.NetSDKLib.EM_SCENE_CLASS_TYPE}
    */
    public int              emClassType;
    /**
     * 预留字段
    */
    public byte[]           szResvered = new byte[252];

    public NET_LINK_CLASS_TYPE() {
    }
}

