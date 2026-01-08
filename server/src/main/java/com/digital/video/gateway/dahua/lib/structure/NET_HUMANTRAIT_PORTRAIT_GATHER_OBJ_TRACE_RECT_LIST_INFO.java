package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 抓拍前的目标轨迹坐标框和时间信息
*/
public class NET_HUMANTRAIT_PORTRAIT_GATHER_OBJ_TRACE_RECT_LIST_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 目标坐标矩形框，绝对坐标系,参见结构体定义 {@link com.netsdk.lib.structure.NET_RECT_EX}
    */
    public NET_RECT_EX      stuOriginalBoundingBox = new NET_RECT_EX();
    /**
     * 该目标框对应的时间,精确到毫秒，UTC 0时区的毫秒值
    */
    public double           dbObjCaptureTime;
    /**
     * 保留字节
    */
    public byte[]           szReserved = new byte[96];

    public NET_HUMANTRAIT_PORTRAIT_GATHER_OBJ_TRACE_RECT_LIST_INFO() {
    }
}

