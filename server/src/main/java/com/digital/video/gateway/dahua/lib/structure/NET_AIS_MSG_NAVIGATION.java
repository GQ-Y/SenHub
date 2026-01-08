package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * 航行信息,当Type类型为1、2、3、18、19等时有效
*/
public class NET_AIS_MSG_NAVIGATION extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 当前纬度信息，单位度
    */
    public double           dLatitude;
    /**
     * 当前经度信息，单位度
    */
    public double           dLongitude;
    /**
     * 对地航向，单位度
    */
    public double           dSpeedOverGround;
    /**
     * 对地速度，单位节
    */
    public double           dCourseOverGround;
    /**
     * 保留字节
    */
    public byte[]           szReserved = new byte[1024];

    public NET_AIS_MSG_NAVIGATION() {
    }
}

