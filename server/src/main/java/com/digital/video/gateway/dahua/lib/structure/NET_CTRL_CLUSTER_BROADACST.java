package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 集群对讲播报内容
*/
public class NET_CTRL_CLUSTER_BROADACST extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 群组编码
    */
    public byte[]           szGroupCode = new byte[64];
    /**
     * 消息Id
    */
    public byte[]           szMsgId = new byte[64];
    /**
     * 用户编码
    */
    public byte[]           szUserCode = new byte[64];
    /**
     * 用户名称
    */
    public byte[]           szUserName = new byte[64];
    /**
     * 发送时间
    */
    public byte[]           szSendTime = new byte[32];
    /**
     * 文件地址
    */
    public byte[]           szFileUrl = new byte[256];
    /**
     * 文件类型, 0: 未知，1: DAV 格式, 2: MP4 格式, 3: MP3 格式, 4: AAC 格式, 5: JPG 格式, 6: MOV 格式, 7: AVI 格式, 8: FLV 格式, 9: WAV 格式, 10: WMA 格式, 11: BMP 格式, 12: PNG 格式, 13: RAW 格式, 14: M4V 格式
    */
    public int              nType;
    /**
     * 保留字节
    */
    public byte[]           szreserved = new byte[260];

    public NET_CTRL_CLUSTER_BROADACST() {
    }
}

