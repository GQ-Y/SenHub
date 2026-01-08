package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 骑车人坐标图信息
*/
public class NET_HUMAN_RECT extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 包围盒(8192坐标系),参见结构体定义 {@link com.netsdk.lib.structure.NET_RECT_EX}
    */
    public NET_RECT_EX      stuBoundingBox = new NET_RECT_EX();
    /**
     * 保留字节
    */
    public byte[]           szReserved = new byte[32];

    public NET_HUMAN_RECT() {
    }
}

