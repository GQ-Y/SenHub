package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;

/**
 * 检测到的物体补充字段
 * @author ： 251823
 * @since ： Created in 2021/11/07 15:22
 */
public class NET_MSG_OBJECT_SUPPLEMENT extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
	/**
	 * 智能物体全局唯一物体标识
	 */
    public byte[]           szObjectUUID = new byte[48];
    /**
     * 渣土车是否遮盖识别,0:渣土车是否遮盖未知,1:渣土车遮盖,2:渣土车无遮盖空载,3:渣土车无遮盖满载.
    */
    public int              nMuckHide;
    /**
     * 货车载货类型,0:货车是否载货未知,1:沙子,2:泥浆,3:石头,4:石渣.
    */
    public int              nCarryType;
    /**
     * 物体类型
    */
    public byte[]           szCategory = new byte[32];
    /**
     * 车载人数
    */
    public int              nHumanNum;
    /**
     * 平台唯一标识车牌字段，区别于UID
    */
    public byte[]           szUUID = new byte[64];
    /**
     * 修改时间，指的是平台最近一次修改注册库人员信息的时间,参见结构体定义 {@link com.netsdk.lib.com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TIME}
    */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TIME stuModifyTime = new com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TIME();
    /**
     * 注册库入库时间,参见结构体定义 {@link com.netsdk.lib.com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TIME}
    */
    public com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TIME stuRegisterStorageTime = new com.digital.video.gateway.dahua.lib.NetSDKLib.NET_TIME();
    /**
     * 车辆朝向,0：车辆朝向未知,1：正面,2：背面,3：侧面
    */
    public int              nDirection;
    /**
     * Text文本内容对应的区域编号
    */
    public int              nTextRegionID;
	/**
	 * 预留字节
	 */        
    public byte[]           szReserved = new byte[92];
}

