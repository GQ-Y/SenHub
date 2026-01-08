package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 报警记录信息
*/
public class NET_RECORD_ALARMRECORD_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    public int              dwSize;
    /**
     * 记录集编号,只读
    */
    public int              nRecNo;
    /**
     * 报警时间,UTC秒数,只读,参见结构体定义 {@link com.netsdk.lib.com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TIME}
    */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TIME stuCreateTime = new com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TIME();
    /**
     * 报警通道号
    */
    public int              nChannelID;
    /**
     * 传感器感应方式,参见枚举定义 {@link com.netsdk.lib.com.digital.video.gateway.dahua.lib.NetSDKLib.NET_SENSE_METHOD}
    */
    public int              emSenseMethod;
    /**
     * 报警房间号
    */
    public byte[]           szRoomNumber = new byte[32];
    /**
     * 0未读,1已读,参见枚举定义 {@link com.netsdk.lib.com.digital.video.gateway.dahua.lib.NetSDKLib.NET_ANNOUNCE_READFLAG}
    */
    public int              emReadFlag;
    /**
     * 备注
    */
    public byte[]           szNotes = new byte[128];

    public NET_RECORD_ALARMRECORD_INFO() {
        this.dwSize = this.size();
    }
}

