package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
import com.sun.jna.Pointer;
/**
 * 文件信息
*/
public class NET_EMERGENCY_FILE_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 原始文件创建时文件的唯一标示
    */
    public byte[]           szUniqueID = new byte[128];
    /**
     * 原始文件创建设备的序列号ID
    */
    public byte[]           szOriginalDeviceID = new byte[128];
    /**
     * 文件的真实类型,参见枚举定义 {@link com.netsdk.lib.enumeration.EM_MOBILE_ENFORCE_FORMAT}
    */
    public int              emFormat;
    /**
     * 文件后缀类型,参见枚举定义 {@link com.netsdk.lib.enumeration.EM_MOBILE_ENFORCE_FILE_TYPE}
    */
    public int              emFileType;
    /**
     * 文件名称
    */
    public byte[]           szFileName = new byte[260];
    /**
     * 文件路径。
    */
    public byte[]           szFilePath = new byte[260];
    /**
     * 文件开始时间,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME stStartTime = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME();
    /**
     * 文件结束时间,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME stEndTime = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME();
    /**
     * 文件长度
    */
    public int              nLength;
    /**
     * UTC开始时间,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME stuStartTimeRealUTC = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME();
    /**
     * UTC结束时间,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME stuEndTimeRealUTC = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME();
    /**
     * 录像标志数组,参见枚举定义 {@link com.netsdk.lib.enumeration.EM_MOBILE_ENFORCE_FLAGS}
    */
    public int[]            emFlags = new int[64];
    /**
     * 文件信息扩展字段,参见结构体定义 {@link com.netsdk.lib.structure.NET_EMERGENCY_FILE_INFO_EXTERN}
    */
    public Pointer          pstuFileInfoExtern;
    /**
     * GPS信息个数
    */
    public int              nGPSInfosNum;
    /**
     * GPS信息内容,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_GPS_STATUS_INFO}
    */
    public Pointer          pstuGPSInfo;
    /**
     * 文件备注信息
    */
    public byte[]           szRemark = new byte[64];
    /**
     * 保留字节
    */
    public byte[]           byReserved = new byte[128-com.hikvision.nvr.dahua.lib.NetSDKLib.POINTERSIZE];

    public NET_EMERGENCY_FILE_INFO() {
    }
}

