package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * CLIENT_RemoteRename 接口输出参数
*/
public class NET_OUT_REMOTE_RENAME extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 结构体大小
    */
    public int              dwSize;

    public NET_OUT_REMOTE_RENAME() {
        this.dwSize = this.size();
    }
}

