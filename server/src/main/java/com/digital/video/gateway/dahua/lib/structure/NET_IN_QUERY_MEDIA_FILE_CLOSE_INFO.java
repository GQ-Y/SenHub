package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * CLIENT_QueryMediaFileClose 接口输入参数
*/
public class NET_IN_QUERY_MEDIA_FILE_CLOSE_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 此结构体大小,必须赋值
    */
    public int              dwSize;
    /**
     * 查询ID号
    */
    public int              nFindID;

    public NET_IN_QUERY_MEDIA_FILE_CLOSE_INFO() {
        this.dwSize = this.size();
    }
}

