package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * CIENT_SetSplitWindowsInfo接口输入参数
*/
public class NET_IN_SPLIT_SET_WINDOWS_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    public int              dwSize;
    /**
     * 画面分割所在的视频输出通道
    */
    public int              nChannel;
    /**
     * 拼接屏ID
    */
    public byte[]           szCompositeID = new byte[64];
    /**
     * 窗口信息,参见结构体定义 {@link com.netsdk.lib.com.digital.video.gateway.dahua.lib.NetSDKLib.NET_BLOCK_COLLECTION}
    */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.NET_BLOCK_COLLECTION stuInfos = new com.digital.video.gateway.dahua.lib.NetSDKLib.NET_BLOCK_COLLECTION();

    public NET_IN_SPLIT_SET_WINDOWS_INFO() {
        this.dwSize = this.size();
    }
}

