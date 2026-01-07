package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 对接人像聚档平台定制使用
*/
public class NET_HUMANTRAIT_PORTRAIT_GATHER_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 目标进出状态，0表示未知，1表示进，2表示出
    */
    public int              nInOutStatus;
    /**
     * 抓拍前的目标轨迹坐标框和时间信息个数
    */
    public int              nObjTraceRectListNum;
    /**
     * 抓拍前的目标轨迹坐标框和时间信息,参见结构体定义 {@link com.netsdk.lib.structure.NET_HUMANTRAIT_PORTRAIT_GATHER_OBJ_TRACE_RECT_LIST_INFO}
    */
    public NET_HUMANTRAIT_PORTRAIT_GATHER_OBJ_TRACE_RECT_LIST_INFO[] stuObjTraceRectList = new NET_HUMANTRAIT_PORTRAIT_GATHER_OBJ_TRACE_RECT_LIST_INFO[64];
    /**
     * 保留字节
    */
    public byte[]           szReserved = new byte[256];

    public NET_HUMANTRAIT_PORTRAIT_GATHER_INFO() {
        for(int i = 0; i < stuObjTraceRectList.length; i++){
            stuObjTraceRectList[i] = new NET_HUMANTRAIT_PORTRAIT_GATHER_OBJ_TRACE_RECT_LIST_INFO();
        }
    }
}

