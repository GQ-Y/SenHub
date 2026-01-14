package com.livox.demo;

/**
 * 点云数据回调接口
 */
public interface PointCloudCallback {
    /**
     * 点云数据回调
     * @param handle 设备句柄
     * @param devType 设备类型
     * @param pointCount 点数
     * @param dataType 数据类型 (0x01=笛卡尔高精度, 0x02=笛卡尔低精度, 0x03=球坐标)
     * @param data 点云数据（字节数组）
     */
    void onPointCloud(int handle, int devType, int pointCount, int dataType, byte[] data);
}
