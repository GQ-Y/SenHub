package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 人员信息
*/
public class NET_PERSONNEL_CATEGORY_PERSON_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 0:未知, 1:客户, 2:员工, 3:押运人员
    */
    public int              nType;
    /**
     * 对应类型人数
    */
    public int              nTotalNum;
    /**
     * 保留字节
    */
    public byte[]           szReserved = new byte[1024];

    public NET_PERSONNEL_CATEGORY_PERSON_INFO() {
    }
}

