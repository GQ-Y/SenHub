package com.digital.video.gateway.driver.livox;

/**
 * Livox 雷达设备信息变化回调接口
 * 当设备连接或断开时，SDK 会自动调用此回调
 */
public interface DeviceInfoCallback {
    
    /**
     * 设备信息变化回调
     * @param handle 设备句柄
     * @param devType 设备类型 (9 = Mid-360)
     * @param serial 设备序列号 (SN)
     * @param ip 设备 IP 地址
     */
    void onDeviceInfoChange(int handle, int devType, String serial, String ip);
}
