package com.digital.video.gateway.dahua.lib.structure;
import com.digital.video.gateway.dahua.lib.NetSDKLib;
/**
 * CLIENT_AttachElevatorFloorCounter 接口出参
*/
public class NET_OUT_ATTACH_ELEVATOR_FLOOR_COUNTER_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 此结构体大小,必须赋值
    */
    public int              dwSize;

    public NET_OUT_ATTACH_ELEVATOR_FLOOR_COUNTER_INFO() {
        this.dwSize = this.size();
    }
}

