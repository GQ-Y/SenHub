package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * CLIENT_AttachXRayPackageManualExport 接口出参
*/
public class NET_OUT_ATTACH_XRAY_PACKAGE_MANUAL_EXPORT extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 此结构体大小,必须赋值
    */
    public int              dwSize;

    public NET_OUT_ATTACH_XRAY_PACKAGE_MANUAL_EXPORT() {
        this.dwSize = this.size();
    }
}

