package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * CLIENT_DoFindLargeModeServer 接口输入参数
*/
public class NET_IN_DO_FIND_LARGE_MODE_SERVER extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 此结构体大小,必须赋值
    */
    public int              dwSize;
    /**
     * 偏移位置
    */
    public int              nOffset;
    /**
     * 查询数量
    */
    public int              nCount;

    public NET_IN_DO_FIND_LARGE_MODE_SERVER() {
        this.dwSize = this.size();
    }
}

