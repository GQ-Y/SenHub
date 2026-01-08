package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 人员重建信息
*/
public class NET_REABSTRACT_PERSON_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 人员唯一标识符
    */
    public byte[]           szUID = new byte[32];
    public byte[]           bReserved = new byte[1024];

    public NET_REABSTRACT_PERSON_INFO() {
    }
}

