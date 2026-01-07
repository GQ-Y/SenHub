package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 目标库路人信息
*/
public class FACERECOGNITION_CUSTOM_PASSER_BY_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 入库地点对应的通道号
    */
    public int              nStorageAddrChannel;
    /**
     * 入库地点(首次抓拍到的地点)对应的预置点号
    */
    public int              nStoragePresetID;
    /**
     * 入库时间,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME stuStorageTime = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME();
    /**
     * 最近出现时间,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME stuLastAppearTime = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME();
    /**
     * 最近出现地点对应的通道号
    */
    public int              nLastAppearAddrChannel;
    /**
     * 最近出现地点对应的预置点号(球机预置点从1开始)
    */
    public int              nLastAppearPresetID;
    /**
     * 出现次数
    */
    public int              nOccurrenceNumber;
    /**
     * 保留字段
    */
    public byte[]           szReserved = new byte[1020];

    public FACERECOGNITION_CUSTOM_PASSER_BY_INFO() {
    }
}

