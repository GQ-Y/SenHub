package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
import com.sun.jna.Pointer;
/**
 * CLIENT_AttachRadarAISInfo接口入参
*/
public class NET_IN_ATTACH_RADAR_AIS_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 结构体大小
    */
    public int              dwSize;
    /**
     * 雷达AIS信息回调,参见回调函数定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.fRadarAISInfoCallBack}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.fRadarAISInfoCallBack cbAISInfo;
    /**
     * 用户数据
    */
    public Pointer          dwUser;

    public NET_IN_ATTACH_RADAR_AIS_INFO() {
        this.dwSize = this.size();
    }
}

