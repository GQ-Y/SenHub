package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
import com.sun.jna.Pointer;
/**
 * 事件类型EVENT_IVS_FACERECOGNITION(目标识别)对应的数据块描述信息
*/
public class DEV_EVENT_FACERECOGNITION_INFO_V2 extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 通道号
    */
    public int              nChannelID;
    /**
     * 事件名称
    */
    public byte[]           szName = new byte[128];
    /**
     * 事件ID
    */
    public int              nEventID;
    /**
     * 事件发生的时间,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME_EX}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME_EX UTC = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME_EX();
    /**
     * 当前人脸匹配到的候选对象数量
    */
    public int              nCandidateNum;
    /**
     * 当前人脸匹配到的候选对象信息,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.CANDIDATE_INFOEX}
    */
    public Pointer          pstuCandidates;
    /**
     * 检测到的物体,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_MSG_OBJECT}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_MSG_OBJECT stuObject = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_MSG_OBJECT();
    /**
     * 保留字节
    */
    public byte[]           szReserved = new byte[10240];

    public DEV_EVENT_FACERECOGNITION_INFO_V2() {
    }
}

