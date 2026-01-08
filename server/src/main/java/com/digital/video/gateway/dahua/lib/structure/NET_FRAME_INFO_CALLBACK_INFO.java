package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 帧信息
*/
public class NET_FRAME_INFO_CALLBACK_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 数据完整校验标识，0:默认不开启,不校验；1：其他类型错误导致未校验；2：经校验,结果失败；3：经校验，结果成功
    */
    public byte             nCompFrameVerifyStatus;
    /**
     * 预留字节
    */
    public byte[]           szReserved = new byte[1023];

    public NET_FRAME_INFO_CALLBACK_INFO() {
    }
}

