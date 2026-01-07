package com.hikvision.nvr.dahua.lib.structure;
import com.sun.jna.Pointer;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
/**
 * 多维度电梯感知信息信息
*/
public class NET_NOTIFY_ELEVATOR_FLOOR_COUNTER_INFO extends com.hikvision.nvr.dahua.lib.NetSDKLib.SdkStructure
{
    /**
     * 梯内当前人数
    */
    public int              nCurrentNumber;
    /**
     * 电梯运行的速度 单位m/s
    */
    public double           dbSpeed;
    /**
     * 楼层号
    */
    public int              nFloorNum;
    /**
     * 温度，单位：℃
    */
    public int              nTemperature;
    /**
     * 电梯加速度 单位 m/s^2
    */
    public double           dbAcceleration;
    /**
     * 振动加速度，单位m/s^2
    */
    public double[]         dbVibrationAcceleration = new double[2];
    /**
     * 电梯内亮度，档次,1 ：弱,2 ：中,3 ：强
    */
    public int              nBrightness;
    /**
     * 上报数据通道号，中间平台使用，IPC上报不带此字段
    */
    public int              nChannel;
    /**
     * 扩展数据, 内存由NetSDK申请释放,参见结构体定义 {@link com.netsdk.lib.structure.NET_NOTIFY_ELEVATOR_FLOOR_COUNTER_EXTDATA_INFO}
    */
    public Pointer          pstuExtData;
    /**
     * 保留字段
    */
    public byte[]           szResvered = new byte[1020-com.hikvision.nvr.dahua.lib.NetSDKLib.POINTERSIZE];

    public NET_NOTIFY_ELEVATOR_FLOOR_COUNTER_INFO() {
    }
}

