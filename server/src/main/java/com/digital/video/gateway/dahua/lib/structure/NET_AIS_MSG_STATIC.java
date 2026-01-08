package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 对应船只静态信息,Type为5,19,24时有效
*/
public class NET_AIS_MSG_STATIC extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 船只名称
    */
    public byte[]           szShipName = new byte[32];
    /**
     * 保留字节
    */
    public byte[]           szReserved = new byte[1024];

    public NET_AIS_MSG_STATIC() {
    }
}

