package com.hikvision.nvr.dahua.lib.structure;


import com.hikvision.nvr.dahua.lib.NetSDKLib;

/** 
* @author 291189
* @description  接口 CLIENT_AttachVehiclesDistributionData 的输出参数 
* @origin autoTool
* @date 2023/07/03 14:07:28
*/
public class NET_OUT_ATTACH_VEHICLES_DISTRIBUTION_DATA extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
/** 
此结构体大小
*/
    public			int            dwSize;

public NET_OUT_ATTACH_VEHICLES_DISTRIBUTION_DATA(){
		this.dwSize=this.size();
}
}

