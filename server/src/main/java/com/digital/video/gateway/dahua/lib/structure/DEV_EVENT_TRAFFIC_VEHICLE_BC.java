package com.digital.video.gateway.dahua.lib.structure;


import com.digital.video.gateway.dahua.lib.NetSDKLib;
import com.sun.jna.Pointer;

import static com.digital.video.gateway.dahua.lib.NetSDKLib.POINTERSIZE;

/** 
* @author 291189
* @description  事件类型EVENT_IVS_TRAFFIC_VEHICLE_BC(飙车事件)对应的数据块描述信息 
* @origin autoTool
* @date 2023/09/06 18:56:07
*/
public class DEV_EVENT_TRAFFIC_VEHICLE_BC extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
/** 
通道号
*/
    public			int            nChannelID;
/** 
0:脉冲
*/
    public			int            nAction;
/** 
事件名称
*/
    public			byte[]         szName = new byte[128];
/** 
时间戳(单位是毫秒)
*/
    public			double         PTS;
/** 
事件发生的时间
*/
    public NET_TIME_EX      UTC = new NET_TIME_EX();
/** 
事件ID
*/
    public			int            nEventID;
/** 
车牌信息
*/
    public com.digital.video.gateway.dahua.lib.NetSDKLib.DH_MSG_OBJECT stuObject = new com.digital.video.gateway.dahua.lib.NetSDKLib.DH_MSG_OBJECT();
/** 
车身信息
*/
    public	com.digital.video.gateway.dahua.lib.NetSDKLib.DH_MSG_OBJECT stuVehicle = new com.digital.video.gateway.dahua.lib.NetSDKLib.DH_MSG_OBJECT();
/** 
交通车辆信息
*/
    public DEV_EVENT_TRAFFIC_TRAFFICCAR_INFO stTrafficCar = new DEV_EVENT_TRAFFIC_TRAFFICCAR_INFO();
/** 
事件组ID，同一辆车抓拍过程内GroupID相同
*/
    public			int            nGroupID;
/** 
一个事件组内的抓拍张数
*/
    public			int            nCountInGroup;
/** 
一个事件组内的抓拍序号
*/
    public			int            nIndexInGroup;
/** 
对应车道号
*/
    public			int            nLane;
/** 
表示抓拍序号,如3,2,1. 1表示抓拍结束,0表示异常结束
*/
    public			int            nSequence;
/** 
公共信息
*/
    public com.digital.video.gateway.dahua.lib.NetSDKLib.EVENT_COMM_INFO stCommInfo = new com.digital.video.gateway.dahua.lib.NetSDKLib.EVENT_COMM_INFO();
/** 
事件对应文件信息
*/
    public com.digital.video.gateway.dahua.lib.NetSDKLib.NET_EVENT_FILE_INFO stFileInfo = new com.digital.video.gateway.dahua.lib.NetSDKLib.NET_EVENT_FILE_INFO();
/** 
图片信息数组, refer to {@link NET_IMAGE_INFO_EX3}
*/
    public Pointer          pstuImageInfo;
/** 
图片信息个数
*/
    public			int            nImageInfoNum;
/** 
预留字段
*/
    public			byte[]         byReserved = new byte[1020-POINTERSIZE];

public DEV_EVENT_TRAFFIC_VEHICLE_BC(){
}
}

