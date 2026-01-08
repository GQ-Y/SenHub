package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 视频流缺陷检测到的目标信息
*/
public class NET_ELECTRIC_FAULT_OBJECTS_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 电力缺陷检测类型
    */
    public byte[]           szFaultType = new byte[32];
    /**
     * 包围盒,参见结构体定义 {@link com.netsdk.lib.structure.NET_RECT_EX}
    */
    public NET_RECT_EX      stuBoundingBox = new NET_RECT_EX();
    /**
     * 物体ID
    */
    public int              nObjectID;
    /**
     * 保留字节
    */
    public byte[]           szReserved = new byte[1020];

    public NET_ELECTRIC_FAULT_OBJECTS_INFO() {
    }
}

