package com.hikvision.nvr.dahua.lib.structure;

import com.hikvision.nvr.dahua.lib.NetSDKLib;

/**
 * @author 291189
 * @version 1.0
 * @description CLIENT_DeleteGroupFromVehicleRegisterDB 接口输出参数
 * @date 2021/8/17 14:16
 */
public class NET_OUT_DELETE_GROUP_FROM_VEHICLE_REG_DB extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
    public int              dwSize;                               // 结构体大小

    public NET_OUT_DELETE_GROUP_FROM_VEHICLE_REG_DB(){
        this.dwSize=this.size();
    }
}

