package com.hikvision.nvr.dahua.lib.structure;

import com.hikvision.nvr.dahua.lib.NetSDKLib;

/**
 * @author 291189
 * @version 1.0
 * @description CLIENT_ModifyVehicleForVehicleRegisterDB 接口输入参数
 * @date 2022/10/22 10:24
 */
public class NET_IN_MODIFY_VEHICLE_FOR_VEHICLE_REG_DB extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
    public int              dwSize;                               // 结构体大小
    public com.hikvision.nvr.dahua.lib.NetSDKLib.NET_VEHICLE_INFO stuVehicleInfo = new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_VEHICLE_INFO(); // 车辆信息

    public NET_IN_MODIFY_VEHICLE_FOR_VEHICLE_REG_DB(){
        dwSize=this.size();
    }
}

