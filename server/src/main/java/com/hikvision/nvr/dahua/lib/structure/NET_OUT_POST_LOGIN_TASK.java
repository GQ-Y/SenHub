package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * CLIENT_PostLoginTask 输出参数
*/
public class NET_OUT_POST_LOGIN_TASK extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 赋值为结构体大小
    */
    public int              dwSize;

    public NET_OUT_POST_LOGIN_TASK() {
        this.dwSize = this.size();
    }
}

