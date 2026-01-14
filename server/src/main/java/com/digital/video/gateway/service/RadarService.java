package com.digital.video.gateway.service;

import com.digital.video.gateway.driver.livox.LivoxDriver;
import com.digital.video.gateway.driver.livox.protocol.SdkPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * 览沃 (Livox) Mid-360 雷达服务
 * 负责接收 UDP 点云数据，解析目标位置，并驱动摄像头 PTZ 联动。
 * Updated to use Netty-based LivoxDriver.
 */
public class RadarService {
    private static final Logger logger = LoggerFactory.getLogger(RadarService.class);

    private final PTZService ptzService;
    private final LivoxDriver livoxDriver; // Netty Driver

    // 联动配置（示例：将雷达目标映射到特定摄像头）
    private String targetDeviceId;
    private int targetChannel = 1;

    public RadarService(PTZService ptzService) {
        this.ptzService = ptzService;
        this.livoxDriver = new LivoxDriver(); // Should ideally be injected
    }

    /**
     * 启动雷达监听
     */
    public synchronized void start() {
        try {
            livoxDriver.start();
            livoxDriver.setPointCloudCallback(this::handlePacket);
            logger.info("雷达监听服务已启动 (Netty Driver)");
        } catch (Exception e) {
            logger.error("启动雷达监听服务失败", e);
        }
    }

    /**
     * 停止雷达监听
     */
    public synchronized void stop() {
        livoxDriver.stop();
        logger.info("雷达监听服务已停止");
    }

    /**
     * 解析 Livox 数据包
     */
    private void handlePacket(SdkPacket packet) {
        if (packet.payload == null || packet.payload.length < 1)
            return;

        // Payload Structure for Data:
        // [0] Data Type (1 = Cartesian)
        // ...

        ByteBuffer bb = ByteBuffer.wrap(packet.payload).order(ByteOrder.LITTLE_ENDIAN);
        byte dataType = bb.get();

        // Mid-360 常见的笛卡尔坐标数据类型 0x01
        if (dataType == 0x01) {
            parseCartesianPoints(bb);
        }
    }

    /**
     * 解析笛卡尔坐标点云 (Type 0x01)
     * 每个点 13 字节: x(4), y(4), z(4), reflectivity(1)
     */
    private void parseCartesianPoints(ByteBuffer bb) {
        List<Point> points = new ArrayList<>();
        while (bb.remaining() >= 13) {
            int xInt = bb.getInt();
            int yInt = bb.getInt();
            int zInt = bb.getInt();

            float x = xInt / 1000.0f; // mm -> m
            float y = yInt / 1000.0f;
            float z = zInt / 1000.0f;
            bb.get(); // reflectivity

            // 简单过滤：只保留 0.5m 到 30m 范围内的目标
            float distance = (float) Math.sqrt(x * x + y * y + z * z);
            if (distance > 0.5f && distance < 30.0f) {
                points.add(new Point(x, y, z));
            }
        }

        if (!points.isEmpty()) {
            processPoints(points);
        }
    }

    /**
     * 目标识别与联动调度
     * 这里简化为取点云的质心作为目标
     */
    private void processPoints(List<Point> points) {
        if (points.size() < 10)
            return; // Ignore noise

        float sumX = 0, sumY = 0, sumZ = 0;
        for (Point p : points) {
            sumX += p.x;
            sumY += p.y;
            sumZ += p.z;
        }
        float centerX = sumX / points.size();
        float centerY = sumY / points.size();
        float centerZ = sumZ / points.size();

        // 计算 PTZ 角度
        // Pan: 水平角，atan2(y, x)
        double pan = Math.toDegrees(Math.atan2(centerY, centerX));
        // Tilt: 垂直角，atan2(z, sqrt(x^2 + y^2))
        double tilt = Math.toDegrees(Math.atan2(centerZ, Math.sqrt(centerX * centerX + centerY * centerY)));

        // 纠正角度范围 (根据摄像头挂载方向调整Offset)
        float finalPan = (float) (pan + 180); // 映射到 0-360
        float finalTilt = (float) (tilt);

        if (targetDeviceId != null) {
            drivePTZ(targetDeviceId, targetChannel, finalPan, finalTilt);
        }
    }

    private void drivePTZ(String deviceId, int channel, float pan, float tilt) {
        ptzService.gotoAngle(deviceId, channel, pan, tilt, 1.0f);
        logger.debug("雷达联动驱动 PTZ: deviceId={}, pan={}, tilt={}", deviceId, pan, tilt);
    }

    // 配置目标设备
    public void setTargetDevice(String deviceId, int channel) {
        this.targetDeviceId = deviceId;
        this.targetChannel = channel;
    }

    private static class Point {
        float x, y, z;

        Point(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
