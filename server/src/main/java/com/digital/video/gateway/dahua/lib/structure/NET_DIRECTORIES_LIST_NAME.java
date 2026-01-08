package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 工作组包含的工作目录名称
*/
public class NET_DIRECTORIES_LIST_NAME extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 名称
    */
    public byte[]           szName = new byte[256];

    public NET_DIRECTORIES_LIST_NAME() {
    }
}

