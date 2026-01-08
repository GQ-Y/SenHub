package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;

/**
 * @author 291189
 * @version 1.0
 * @description  CLIENT_StopFindVehicleRegisterDB 接口输入参数
 * @date 2022/10/22 10:56
 */
public class NET_IN_STOP_FIND_VEHICLE_REG_DB extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
    public int              dwSize;                               // 结构体大小
    public int              nToken;                               // 查询令牌

    public NET_IN_STOP_FIND_VEHICLE_REG_DB(){
        dwSize=this.size();
    }
}

