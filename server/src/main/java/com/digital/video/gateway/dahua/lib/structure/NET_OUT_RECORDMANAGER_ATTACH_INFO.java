package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * CLIENT_AttachRecordManagerState 出参
*/
public class NET_OUT_RECORDMANAGER_ATTACH_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 结构体大小
    */
    public int              dwSize;

    public NET_OUT_RECORDMANAGER_ATTACH_INFO() {
        this.dwSize = this.size();
    }
}

