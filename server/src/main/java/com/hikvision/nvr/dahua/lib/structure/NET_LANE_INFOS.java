package com.hikvision.nvr.dahua.lib.structure;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 车道信息
*/
public class NET_LANE_INFOS extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 车辆所在的车道类型（后续可以扩展）"Normal": 普通车道（默认值）;"EmergencyLane":应急车道
    */
    public byte[]           szLaneType = new byte[32];
    /**
     * 行驶方向 "Approach"-上行，即车辆离设备部署点越来越近;"Leave"-下行，即车辆离设备部署点越来越远
    */
    public byte[]           szLaneDirection = new byte[32];
    /**
     * 关联的目标索引，与多目标报警数组下标对应，从0开始
    */
    public int              nIndex;
    /**
     * 预留字节
    */
    public byte[]           szReserved = new byte[508];

    public NET_LANE_INFOS() {
    }
}

