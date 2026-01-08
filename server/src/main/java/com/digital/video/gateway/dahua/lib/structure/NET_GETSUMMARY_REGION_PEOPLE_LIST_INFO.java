package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * @author 251823
 * @description 区域人数统计列表
 * @date 2022/01/07
 */
public class NET_GETSUMMARY_REGION_PEOPLE_LIST_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
    /**
     *  区域ID
     */
    public int              nRegionID;
    /**
     *  区域人数
     */
    public int              nRegionPeopleNum;
    /**
     *  区域顶点个数
     */
    public int              nRegionPointNum;
    /**
     *  区域顶点坐标
     */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.NET_POINT[] stuRegionPoint = (com.digital.video.gateway.dahua.lib.NetSDKLib.NET_POINT[]) new com.digital.video.gateway.dahua.lib.NetSDKLib.NET_POINT().toArray(20);
    /**
     *  保留字节
     */
    public byte[]           szReserved = new byte[1024];
}

