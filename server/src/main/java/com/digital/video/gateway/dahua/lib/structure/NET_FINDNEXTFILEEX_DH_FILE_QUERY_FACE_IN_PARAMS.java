package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 枚举EM_FINDNEXTFILEEX_DH_FILE_QUERY_FACE 对应的入参结构体
*/
public class NET_FINDNEXTFILEEX_DH_FILE_QUERY_FACE_IN_PARAMS extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 此结构体大小,必须赋值
    */
    public int              dwSize;
    /**
     * 查找句柄
    */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.LLong  lFindHandle;
    /**
     * 查找数量
    */
    public int              nFilecount;

    public NET_FINDNEXTFILEEX_DH_FILE_QUERY_FACE_IN_PARAMS() {
        this.dwSize = this.size();
    }
}

