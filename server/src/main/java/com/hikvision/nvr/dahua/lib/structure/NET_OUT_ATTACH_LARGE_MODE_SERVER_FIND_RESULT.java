package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * CLIENT_AttachLargeModeServerFindResult 输出参数
*/
public class NET_OUT_ATTACH_LARGE_MODE_SERVER_FIND_RESULT extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 此结构体大小,必须赋值
    */
    public int              dwSize;

    public NET_OUT_ATTACH_LARGE_MODE_SERVER_FIND_RESULT() {
        this.dwSize = this.size();
    }
}

