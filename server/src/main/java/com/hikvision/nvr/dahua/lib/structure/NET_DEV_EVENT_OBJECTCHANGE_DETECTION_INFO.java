package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 事件类型 EVENT_IVS_OBJECTCHANGE_DETECTION(变化事件目标变化检测)对应的数据块描述信息
*/
public class NET_DEV_EVENT_OBJECTCHANGE_DETECTION_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 通道号
    */
    public int              nChannelID;
    /**
     * 0:脉冲,1:开始, 2:停止
    */
    public int              nAction;
    /**
     * 扩展协议字段,参见结构体定义 {@link com.netsdk.lib.structure.NET_EVENT_INFO_EXTEND}
    */
    public NET_EVENT_INFO_EXTEND stuEventInfoEx = new NET_EVENT_INFO_EXTEND();
    /**
     * 事件发生的时间,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME_EX}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME_EX stuUTC = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME_EX();
    /**
     * 事件编号，用来唯一标志一个事件
    */
    public int              nEventID;
    /**
     * 事件名称
    */
    public byte[]           szName = new byte[128];
    /**
     * 智能事件所属大类
    */
    public byte[]           szClass = new byte[16];
    /**
     * 相对事件时间戳,单位毫秒
    */
    public double           dbPTS;
    /**
     * 智能事件规则编号
    */
    public int              nRuleID;
    /**
     * 帧序号
    */
    public int              nSequence;
    /**
     * 视频分析帧序号
    */
    public int              nFrameSequence;
    /**
     * GroupID事件组ID，同一物体抓拍过程内GroupID相同
    */
    public int              nGroupID;
    /**
     * CountInGroup一个事件组内的抓拍张数
    */
    public int              nCountInGroup;
    /**
     * IndexInGroup一个事件组内的抓拍序号，从1开始
    */
    public int              nIndexInGroup;
    /**
     * 全景广角图,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.SCENE_IMAGE_INFO_EX}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.SCENE_IMAGE_INFO_EX stuSceneImage = new com.hikvision.nvr.dahua.lib.NetSDKLib.SCENE_IMAGE_INFO_EX();
    /**
     * 物体信息列表,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_MSG_OBJECT}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_MSG_OBJECT[] stuObjects = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_MSG_OBJECT[10];
    /**
     * 物体信息列表实际数量
    */
    public int              nObjectsCount;
    /**
     * 规则使用的变化事件算法id
    */
    public int              nAlgId;
    /**
     * 算法名称
    */
    public byte[]           szAlgName = new byte[128];
    /**
     * 自定义报警ID
    */
    public int              nAlarmId;
    /**
     * 预留字节
    */
    public byte[]           szReserverd = new byte[1020];

    public NET_DEV_EVENT_OBJECTCHANGE_DETECTION_INFO() {
        for(int i = 0; i < stuObjects.length; i++){
            stuObjects[i] = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_MSG_OBJECT();
        }
    }
}

