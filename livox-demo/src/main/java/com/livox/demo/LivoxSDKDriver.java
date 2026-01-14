package com.livox.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 基于官方 SDK (JNI) 的 Livox 驱动
 */
public class LivoxSDKDriver {

    private static final Logger log = LoggerFactory.getLogger(LivoxSDKDriver.class);

    private PointCloudCallback javaCallback;
    private PointCloudStats stats;

    public LivoxSDKDriver(PointCloudStats stats) {
        this.stats = stats;
    }

    /**
     * 初始化并启动驱动
     * @param configPath 配置文件路径
     * @return 是否成功
     */
    public boolean start(String configPath) {
        log.info("初始化 Livox SDK (JNI)...");

        // 设置 Java 回调
        javaCallback = (handle, devType, pointCount, dataType, data) -> {
            handlePointCloud(handle, devType, pointCount, dataType, data);
        };

        try {
            // 初始化 SDK
            if (!LivoxJNI.init(configPath)) {
                log.error("Livox SDK 初始化失败");
                return false;
            }

            // 设置点云回调
            LivoxJNI.setPointCloudCallback(javaCallback);

            // 启动 SDK
            if (!LivoxJNI.start()) {
                log.error("Livox SDK 启动失败");
                return false;
            }

            log.info("Livox SDK 启动成功");
            return true;

        } catch (Exception e) {
            log.error("启动 Livox SDK 时出错", e);
            return false;
        }
    }

    /**
     * 停止驱动
     */
    public void stop() {
        log.info("停止 Livox SDK...");
        try {
            LivoxJNI.stop();
            log.info("Livox SDK 已停止");
        } catch (Exception e) {
            log.error("停止 Livox SDK 时出错", e);
        }
    }

    /**
     * 处理点云数据
     */
    private void handlePointCloud(int handle, int devType, int pointCount, int dataType, byte[] data) {
        try {
            // 解析点云数据
            if (dataType == 0x01) { // 笛卡尔高精度坐标
                parseCartesianHighData(pointCount, data);
            } else if (dataType == 0x02) { // 笛卡尔低精度坐标
                parseCartesianLowData(pointCount, data);
            } else {
                log.debug("跳过不支持的数据类型: {}", dataType);
            }
        } catch (Exception e) {
            log.error("处理点云数据时出错", e);
        }
    }

    /**
     * 解析笛卡尔高精度坐标点云
     * 每个点 13 字节: x(4), y(4), z(4), reflectivity(1), tag(1)
     */
    private void parseCartesianHighData(int pointCount, byte[] data) {
        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        
        PointCloudStats.FrameStats frameStats = new PointCloudStats.FrameStats();
        frameStats.frameNumber = stats.totalFrames + 1;
        frameStats.pointCount = pointCount;
        frameStats.timestamp = new java.util.Date();

        float minX = Float.MAX_VALUE, maxX = Float.MIN_VALUE;
        float minY = Float.MAX_VALUE, maxY = Float.MIN_VALUE;
        float minZ = Float.MAX_VALUE, maxZ = Float.MIN_VALUE;
        float sumDistance = 0;
        float sumX = 0, sumY = 0, sumZ = 0;

        for (int i = 0; i < pointCount && bb.remaining() >= 13; i++) {
            // 读取坐标（毫米单位）
            int xInt = bb.getInt();
            int yInt = bb.getInt();
            int zInt = bb.getInt();
            byte reflectivity = bb.get();
            byte tag = bb.get();

            // 转换为米
            float x = xInt / 1000.0f;
            float y = yInt / 1000.0f;
            float z = zInt / 1000.0f;

            // 更新范围
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);

            // 计算距离
            float distance = (float) Math.sqrt(x * x + y * y + z * z);
            sumDistance += distance;

            // 累加坐标
            sumX += x;
            sumY += y;
            sumZ += z;
        }

        // 计算统计信息
        frameStats.minX = minX;
        frameStats.maxX = maxX;
        frameStats.minY = minY;
        frameStats.maxY = maxY;
        frameStats.minZ = minZ;
        frameStats.maxZ = maxZ;
        frameStats.avgDistance = sumDistance / pointCount;
        frameStats.centroidX = sumX / pointCount;
        frameStats.centroidY = sumY / pointCount;
        frameStats.centroidZ = sumZ / pointCount;

        float xRange = maxX - minX;
        float yRange = maxY - minY;
        float area = xRange * yRange;
        frameStats.density = area > 0 ? pointCount / area : 0;

        // 更新累计统计
        stats.totalFrames++;
        stats.totalPoints += pointCount;

        // 输出统计信息
        stats.printStats(frameStats);
    }

    /**
     * 解析笛卡尔低精度坐标点云
     * 每个点 7 字节: x(2), y(2), z(2), reflectivity(1)
     */
    private void parseCartesianLowData(int pointCount, byte[] data) {
        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        
        PointCloudStats.FrameStats frameStats = new PointCloudStats.FrameStats();
        frameStats.frameNumber = stats.totalFrames + 1;
        frameStats.pointCount = pointCount;
        frameStats.timestamp = new java.util.Date();

        float minX = Float.MAX_VALUE, maxX = Float.MIN_VALUE;
        float minY = Float.MAX_VALUE, maxY = Float.MIN_VALUE;
        float minZ = Float.MAX_VALUE, maxZ = Float.MIN_VALUE;
        float sumDistance = 0;
        float sumX = 0, sumY = 0, sumZ = 0;

        for (int i = 0; i < pointCount && bb.remaining() >= 7; i++) {
            // 读取坐标（厘米单位）
            short xShort = bb.getShort();
            short yShort = bb.getShort();
            short zShort = bb.getShort();
            byte reflectivity = bb.get();

            // 转换为米
            float x = xShort / 100.0f;
            float y = yShort / 100.0f;
            float z = zShort / 100.0f;

            // 更新范围
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);

            // 计算距离
            float distance = (float) Math.sqrt(x * x + y * y + z * z);
            sumDistance += distance;

            // 累加坐标
            sumX += x;
            sumY += y;
            sumZ += z;
        }

        // 计算统计信息（与高精度相同）
        frameStats.minX = minX;
        frameStats.maxX = maxX;
        frameStats.minY = minY;
        frameStats.maxY = maxY;
        frameStats.minZ = minZ;
        frameStats.maxZ = maxZ;
        frameStats.avgDistance = sumDistance / pointCount;
        frameStats.centroidX = sumX / pointCount;
        frameStats.centroidY = sumY / pointCount;
        frameStats.centroidZ = sumZ / pointCount;

        float xRange = maxX - minX;
        float yRange = maxY - minY;
        float area = xRange * yRange;
        frameStats.density = area > 0 ? pointCount / area : 0;

        // 更新累计统计
        stats.totalFrames++;
        stats.totalPoints += pointCount;

        // 输出统计信息
        stats.printStats(frameStats);
    }
}
