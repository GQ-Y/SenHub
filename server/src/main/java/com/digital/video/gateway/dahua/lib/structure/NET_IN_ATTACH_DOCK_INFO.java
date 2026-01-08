package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
import com.sun.jna.Pointer;
/**
 * CLIENT_AttachDockInfo 接口输入参数
*/
public class NET_IN_ATTACH_DOCK_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 结构体大小
    */
    public int              dwSize;
    /**
     * 保留字段
    */
    public byte[]           szReserved = new byte[4];
    /**
     * 任务状态回调函数,参见回调函数定义 {@link com.netsdk.lib.com.digital.video.gateway.dahua.lib.NetSDKLib.fUAVDockInfo}
    */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.fUAVDockInfo cbNotify;
    /**
     * 用户信息
    */
    public Pointer          dwUser;

    public NET_IN_ATTACH_DOCK_INFO() {
        this.dwSize = this.size();
    }
}

