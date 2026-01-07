package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * CLIENT_SecurityPrepareExportData接口出参
*/
public class NET_OUT_SECURITY_PREPARE_EXPORT_DATA_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 此结构体大小,必须赋值
    */
    public int              dwSize;

    public NET_OUT_SECURITY_PREPARE_EXPORT_DATA_INFO() {
        this.dwSize = this.size();
    }
}

