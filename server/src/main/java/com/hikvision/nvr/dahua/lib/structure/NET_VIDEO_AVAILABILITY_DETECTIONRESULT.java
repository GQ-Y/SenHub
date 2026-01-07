package com.hikvision.nvr.dahua.lib.structure;


import com.hikvision.nvr.dahua.lib.NetSDKLib;

/** 
* @author 291189
* @description  视频完好率监测结果 
* @date 2022/08/03 14:39:35
*/
public class NET_VIDEO_AVAILABILITY_DETECTIONRESULT extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure {
/** 
结构体大小
*/
    public			int            dwSize;
/** 
信令延时,-1表示检测失败, 单位ms
*/
    public			int            nSignalDelay;
/** 
视频流延时,-1表示检测失败, 单位ms
*/
    public			int            nStreamDelay;
/** 
I帧延时,-1表示检测失败 单位ms
*/
    public			int            nIFrameDelay;

public NET_VIDEO_AVAILABILITY_DETECTIONRESULT(){
		this.dwSize=this.size();
}
}

