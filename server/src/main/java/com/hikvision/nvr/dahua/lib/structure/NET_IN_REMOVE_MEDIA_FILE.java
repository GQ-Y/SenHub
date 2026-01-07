package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * CLIENT_RemoveMediaFile接口输入参数
*/
public class NET_IN_REMOVE_MEDIA_FILE extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 此结构体大小,必须赋值
    */
    public int              dwSize;
    /**
     * 删除方式：0-按文件查询条件删除
    */
    public int              nMode;
    /**
     * 开始时间,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME stuStartTime = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME();
    /**
     * 结束时间,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME stuEndTime = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME();
    /**
     * 视频码流 0-未知 1-主码流 2-辅码流1 3-辅码流2 4-辅码流3  5-所有的辅码流类型 6-辅码流4 7-辅码流5 8-辅码流6 9-辅码流7 10-辅码流8 11-辅码流9 12-辅码流10 13-辅码流11 14-辅码流12
    */
    public int              nVideoStream;
    /**
     * 通道号数组个数
    */
    public int              nChannelNum;
    /**
     * 通道号数组
    */
    public int[]            nChannels = new int[1024];
    /**
     * 文件类型个数, 为0表示查询任意类型
    */
    public int              nFileTypeNum;
    /**
     * 文件类型,参见枚举定义 {@link com.netsdk.lib.enumeration.EM_FINDFILE_TYPE}
    */
    public int[]            emFileType = new int[32];

    public NET_IN_REMOVE_MEDIA_FILE() {
        this.dwSize = this.size();
    }
}

