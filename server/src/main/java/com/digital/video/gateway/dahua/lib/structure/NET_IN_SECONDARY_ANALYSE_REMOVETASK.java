package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 删除录像二次分析任务输入参数
*/
public class NET_IN_SECONDARY_ANALYSE_REMOVETASK extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 赋值为结构体大小
    */
    public int              dwSize;
    /**
     * 任务数量, 0表示删除全部
    */
    public int              nTaskIdNum;
    /**
     * 任务ID
    */
    public int[]            nTaskID = new int[32];

    public NET_IN_SECONDARY_ANALYSE_REMOVETASK() {
        this.dwSize = this.size();
    }
}

