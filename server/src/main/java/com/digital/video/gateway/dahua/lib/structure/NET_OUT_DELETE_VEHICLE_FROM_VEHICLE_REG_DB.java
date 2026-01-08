package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;

/**
 * @author 291189
 * @version 1.0
 * @description  CLIENT_DeleteVehicleFromVehicleRegisterDB 接口输出参数
 * @date 2022/10/22 10:33
 */
public class NET_OUT_DELETE_VEHICLE_FROM_VEHICLE_REG_DB extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
    public int              dwSize;                               // 结构体大小

public NET_OUT_DELETE_VEHICLE_FROM_VEHICLE_REG_DB(){
    dwSize=this.size();
}
}

