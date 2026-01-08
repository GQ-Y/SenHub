package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 人群分布图规则报表数据
*/
public class NET_CROWD_DISTRI_MAP extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 人群分布图数据列表,参见结构体定义 {@link com.netsdk.lib.structure.NET_CROWD_DISTRI_MAP_DATA_LIST}
    */
    public NET_CROWD_DISTRI_MAP_DATA_LIST[] stuDataList = new NET_CROWD_DISTRI_MAP_DATA_LIST[64];
    /**
     * 人群分布图数据列表个数
    */
    public int              nDataListCount;
    /**
     * 保留字段
    */
    public byte[]           szReserved = new byte[1020];

    public NET_CROWD_DISTRI_MAP() {
        for(int i = 0; i < stuDataList.length; i++){
            stuDataList[i] = new NET_CROWD_DISTRI_MAP_DATA_LIST();
        }
    }
}

