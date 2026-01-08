package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * CLIENT_StartFindLargeModeServer 接口输出参数
*/
public class NET_OUT_START_FIND_LARGE_MODE_SERVER extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 此结构体大小,必须赋值
    */
    public int              dwSize;
    /**
     * 取到的查询令牌
    */
    public int              nToken;

    public NET_OUT_START_FIND_LARGE_MODE_SERVER() {
        this.dwSize = this.size();
    }
}

