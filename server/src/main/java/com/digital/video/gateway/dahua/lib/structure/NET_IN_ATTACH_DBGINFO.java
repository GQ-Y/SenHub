package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
import com.sun.jna.Pointer;
/**
 * 订阅日志回调入参
*/
public class NET_IN_ATTACH_DBGINFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    public int              dwSize;
    /**
     * 日志等级,参见枚举定义 {@link com.netsdk.lib.enumeration.EM_DBGINFO_LEVEL}
    */
    public int              emLevel;
    /**
     * 回调,参见回调函数定义 {@link com.netsdk.lib.com.digital.video.gateway.dahua.lib.NetSDKLib.fDebugInfoCallBack}
    */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.fDebugInfoCallBack pCallBack;
    /**
     * 用户数据
    */
    public Pointer          dwUser;

    public NET_IN_ATTACH_DBGINFO() {
        this.dwSize = this.size();
    }
}

