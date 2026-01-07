package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * CLIENT_StartRemoteUploadFile 接口输出参数(上传文件到前端设备)
*/
public class NET_OUT_REMOTE_UPLOAD_FILE extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 结构体大小
    */
    public int              dwSize;

    public NET_OUT_REMOTE_UPLOAD_FILE() {
        this.dwSize = this.size();
    }
}

