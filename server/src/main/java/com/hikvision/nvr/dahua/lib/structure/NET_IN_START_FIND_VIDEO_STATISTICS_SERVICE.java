package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * CLIENT_StartFindVideoStatisticsService 接口输入参数
*/
public class NET_IN_START_FIND_VIDEO_STATISTICS_SERVICE extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 此结构体大小,必须赋值
    */
    public int              dwSize;
    /**
     * 开始时间,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME stuStartTime = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME();
    /**
     * 结束时间,参见结构体定义 {@link com.netsdk.lib.com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME}
    */
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME stuEndTime = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_TIME();
    /**
     * 业务大类
    */
    public byte[]           szClassType = new byte[32];
    /**
     * 视频通道号
    */
    public int              nChannel;
    /**
     * 预置点编号
    */
    public int              nPresetId;
    /**
     * 规则名称查询个数
    */
    public int              nRuleNameListNum;
    /**
     * 根据规则名称查询
    */
    public BYTE_ARRAY_32[]  szRuleNameList = new BYTE_ARRAY_32[16];
    /**
     * 区域名称查询个数
    */
    public int              nAreaNameListNum;
    /**
     * 根据区域名称查询
    */
    public BYTE_ARRAY_32[]  szAreaNameList = new BYTE_ARRAY_32[16];
    /**
     * 查询粒度, 0:未知, 1:按天查询, 2:按周查询, 3:按月查询
    */
    public int              nGranularity;
    /**
     * 规则名称扩展字段查询个数
    */
    public int              nRuleNameListExNum;
    /**
     * 区域名称扩展字段查询个数
    */
    public int              nAreaNameListExNum;
    /**
     * 根据规则名称扩展字段查询
    */
    public BYTE_ARRAY_128[] szRuleNameListEx = new BYTE_ARRAY_128[16];
    /**
     * 根据区域名称扩展字段查询
    */
    public BYTE_ARRAY_128[] szAreaNameListEx = new BYTE_ARRAY_128[16];

    public NET_IN_START_FIND_VIDEO_STATISTICS_SERVICE() {
        this.dwSize = this.size();
        for(int i = 0; i < szRuleNameList.length; i++){
            szRuleNameList[i] = new BYTE_ARRAY_32();
        }
        for(int i = 0; i < szAreaNameList.length; i++){
            szAreaNameList[i] = new BYTE_ARRAY_32();
        }
        for(int i = 0; i < szRuleNameListEx.length; i++){
            szRuleNameListEx[i] = new BYTE_ARRAY_128();
        }
        for(int i = 0; i < szAreaNameListEx.length; i++){
            szAreaNameListEx[i] = new BYTE_ARRAY_128();
        }
    }
}

