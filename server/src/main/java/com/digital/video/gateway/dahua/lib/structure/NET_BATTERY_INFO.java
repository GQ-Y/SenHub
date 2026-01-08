package com.digital.video.gateway.dahua.lib.structure;

import com.digital.video.gateway.dahua.lib.NetSDKLib;

/**
 * className：NET_BATTERY_INFO
 * description：
 * author：251589
 * createTime：2021/2/25 15:51
 *
 * @version v1.0
 */

public class NET_BATTERY_INFO extends com.digital.video.gateway.dahua.lib.NetSDKLib.SdkStructure {
    /**
     * dwSize;
     */
    public int              dwSize;
    /**
     *  电池容量百分比
     */
    public int              nPercent;
    /**
     *  是否正在充电
     */
    public int              bCharging;
    /**
     *  电池在位状态
     */
    public int              emExistState;
    /**
     *  电池电量状态
     */
    public int              emState;
    /**
     *  电池电压
     */
    public float            fVoltage;
    /**
     *  电池温度状态,参考枚举{ @link EM_BATTERY_TEMPER_STATE}
     */
    public int              emTemperState;
    /**
     * 电池健康度：0,1,2,3 分别表示：优，良，中，差
    */
    public int              nBatteryHealthy;
    /**
     * 充电功率，单位W，数值范围0~30000W
    */
    public int              nChargingRate;
    /**
     * 放电功率，单位W，数值范围0~10000W
    */
    public int              nDisChargingRate;
    /**
     * 电量剩余使用时长（单位分钟）
    */
    public int              nBatteryLife;
    /**
     * 太阳能控制器是否接入设备(太阳能控制器可以一对多供电，但是只能一对一接入通信，需要通知DSS接入状态)
    */
    public int              bSolarControllerConnected;
    /**
     * 电池温度
    */
    public float            fTemperature;

    public NET_BATTERY_INFO(){
        this.dwSize = this.size();
    }
}

