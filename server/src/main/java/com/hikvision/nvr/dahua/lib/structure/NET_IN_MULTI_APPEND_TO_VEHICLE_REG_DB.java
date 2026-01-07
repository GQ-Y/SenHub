package com.hikvision.nvr.dahua.lib.structure;


import com.hikvision.nvr.dahua.lib.NetSDKLib;
import com.sun.jna.Pointer;

/**
 * @author 291189
 * @version 1.0
 * @description CLIENT_MultiAppendToVehicleRegisterDB 接口输入参数
 * @date 2021/8/17 14:49
 */
public class NET_IN_MULTI_APPEND_TO_VEHICLE_REG_DB extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
    public  int             dwSize;                               // 结构体大小
    public  int             nVehicleNum;                          // 车辆个数
        //public   com.hikvision.nvr.dahua.lib.NetSDKLib.NET_VEHICLE_INFO[] 		stuVehicleInfo= (com.hikvision.nvr.dahua.lib.NetSDKLib.NET_VEHICLE_INFO[])new com.hikvision.nvr.dahua.lib.NetSDKLib.NET_VEHICLE_INFO().toArray(1000);// 车辆信息
    public Pointer          stuVehicleInfo;                       //sizeof(NET_VEHICLE_INFO)*nVehicleNum
    public int              bReplace;                             // 遇到相同记录是否覆盖, TRUE覆盖, FALSE跳过

       public NET_IN_MULTI_APPEND_TO_VEHICLE_REG_DB(){
                    this.dwSize=this.size();
        }
}

