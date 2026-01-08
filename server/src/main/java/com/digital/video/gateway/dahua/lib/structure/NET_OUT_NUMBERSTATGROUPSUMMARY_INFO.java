package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 分组人员统计--获取摘要信息 输出参数
*/
public class NET_OUT_NUMBERSTATGROUPSUMMARY_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 结构体大小
    */
    public int              dwSize;
    /**
     * 摘要信息,参见结构体定义 {@link com.netsdk.lib.structure.NET_NUMBERSTATGROUP_SUMMARY}
    */
    public NET_NUMBERSTATGROUP_SUMMARY stuSummary = new NET_NUMBERSTATGROUP_SUMMARY();

    public NET_OUT_NUMBERSTATGROUPSUMMARY_INFO() {
        this.dwSize = this.size();
    }
}

