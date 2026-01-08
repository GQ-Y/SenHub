package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * CLIENT_DevStorageFormatPartitionAsyn 接口输出参数
*/
public class NET_OUT_DEVSTORAGE_FORMAT_PARTITION_ASYN extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 结构体大小
    */
    public int              dwSize;

    public NET_OUT_DEVSTORAGE_FORMAT_PARTITION_ASYN() {
        this.dwSize = this.size();
    }
}

