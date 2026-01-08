package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * CLIENT_AttachResultOfFindHistoryByPic接口输出参数
*/
public class NET_OUT_ATTACH_RESULT_FINDHISTORY_BYPIC extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 结构体大小
    */
    public int              dwSize;

    public NET_OUT_ATTACH_RESULT_FINDHISTORY_BYPIC() {
        this.dwSize = this.size();
    }
}

