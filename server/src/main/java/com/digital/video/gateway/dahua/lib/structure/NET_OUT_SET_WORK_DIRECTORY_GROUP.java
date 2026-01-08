package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * CLIENT_SetWorkDirectoryGoup 接口输出参数
*/
public class NET_OUT_SET_WORK_DIRECTORY_GROUP extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 结构体大小
    */
    public int              dwSize;

    public NET_OUT_SET_WORK_DIRECTORY_GROUP() {
        this.dwSize = this.size();
    }
}

