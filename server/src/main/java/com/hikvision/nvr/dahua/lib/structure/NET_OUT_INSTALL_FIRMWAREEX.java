package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * CLIENT_UpgraderInstall接口的 EM_UPGRADER_INSTALL_FIRMWAREEX命令出参
*/
public class NET_OUT_INSTALL_FIRMWAREEX extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 结构体大小
    */
    public int              dwSize;

    public NET_OUT_INSTALL_FIRMWAREEX() {
        this.dwSize = this.size();
    }
}

