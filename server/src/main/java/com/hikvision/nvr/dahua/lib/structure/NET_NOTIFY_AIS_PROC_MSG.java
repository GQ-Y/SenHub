package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * AIS订阅上报回调消息
*/
public class NET_NOTIFY_AIS_PROC_MSG extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 从接收器接收的原始AIS消息BASE64字符串
    */
    public byte[]           szMsgStr = new byte[256];
    /**
     * 航行信息,当Type类型为1、2、3、18、19等时有效,参见结构体定义 {@link com.netsdk.lib.structure.NET_AIS_MSG_NAVIGATION}
    */
    public NET_AIS_MSG_NAVIGATION stuNavigation = new NET_AIS_MSG_NAVIGATION();
    /**
     * 时间戳，标准UTC时间
    */
    public long             nTimestamp;
    /**
     * 船只mmsi信息，9位数字组成
    */
    public byte[]           szMMSI = new byte[32];
    /**
     * 对应船只静态信息,Type为5,19,24时有效,参见结构体定义 {@link com.netsdk.lib.structure.NET_AIS_MSG_STATIC}
    */
    public NET_AIS_MSG_STATIC stuStatic = new NET_AIS_MSG_STATIC();
    /**
     * 事件产生UTC，带时区
    */
    public long             nUTC;
    /**
     * 事件产生标准UTC
    */
    public long             nRealUTC;
    /**
     * AIS Message Type参考AIS协议中的 Message ID
    */
    public int              nMessageType;
    /**
     * 保留字节
    */
    public byte[]           szReserved = new byte[1020];

    public NET_NOTIFY_AIS_PROC_MSG() {
    }
}

