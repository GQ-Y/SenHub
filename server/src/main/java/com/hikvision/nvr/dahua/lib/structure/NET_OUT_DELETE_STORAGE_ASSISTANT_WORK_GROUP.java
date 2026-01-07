package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * CLIENT_DeleteStorageAssistantWorkGroup 接口输出参数
*/
public class NET_OUT_DELETE_STORAGE_ASSISTANT_WORK_GROUP extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 结构体大小
    */
    public int              dwSize;

    public NET_OUT_DELETE_STORAGE_ASSISTANT_WORK_GROUP() {
        this.dwSize = this.size();
    }
}

