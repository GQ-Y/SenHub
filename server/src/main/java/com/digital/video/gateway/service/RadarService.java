package com.digital.video.gateway.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 览沃 (Livox) Mid-360 雷达服务
 * 负责接收 UDP 点云数据，解析目标位置，并驱动摄像头 PTZ 联动。
 */
public class RadarService {
    private static final Logger logger = LoggerFactory.getLogger(RadarService.class);
    private static final int LIVOX_UDP_PORT = 56186;
    private static final int BUFFER_SIZE = 1500;

    private final PTZService ptzService;
    private DatagramSocket socket;
    private boolean running = false;
    private ExecutorService executorService;

    // 联动配置（示例：将雷达目标映射到特定摄像头）
    private String targetDeviceId;
    private int targetChannel = 1;

    public RadarService(PTZService ptzService) {
        this.ptzService = ptzService;
    }

    /**
     * 启动雷达监听
     */
    public synchronized void start() {
        if (running)
            return;

        try {
            socket = new DatagramSocket(LIVOX_UDP_PORT);
            running = true;
            executorService = Executors.newSingleThreadExecutor();
            executorService.submit(this::receiveLoop);
            logger.info("雷达监听服务已启动，端口: {}", LIVOX_UDP_PORT);
        } catch (Exception e) {
            logger.error("启动雷达监听服务失败", e);
        }
    }

    /**
     * 停止雷达监听
     */
    public synchronized void stop() {
        running = false;
        if (socket != null) {
            socket.close();
        }
        if (executorService != null) {
            executorService.shutdownNow();
        }
        logger.info("雷达监听服务已停止");
    }

    private void receiveLoop() {
        byte[] buffer = new byte[BUFFER_SIZE];
        long packetCount = 0;
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                packetCount++;
                if (packetCount % 500 == 0) {
                    logger.info("已累计接收到 {} 个雷达数据包", packetCount);
                }
                parsePacket(packet.getData(), packet.getLength());
            } catch (Exception e) {
                if (running) {
                    logger.warn("雷达数据接收异常: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * 解析 Livox 数据包
     * 协议头 (12字节) + 载荷
     */
    private void parsePacket(byte[] data, int length) {
        if (length < 12)
            return;

        ByteBuffer bb = ByteBuffer.wrap(data, 0, length);
        bb.order(ByteOrder.LITTLE_ENDIAN);

        // 跳过协议头
        // byte version = bb.get();
        // short length_val = bb.getShort();
        // ...
        bb.position(10);
        byte dataType = bb.get(); // 载荷数据类型
        // byte slot = bb.get();

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
            float x = bb.getInt() / 1000.0f; // mm -> m
            float y = bb.getInt() / 1000.0f;
            float z = bb.getInt() / 1000.0f;
            bb.get(); // reflectivity

            // 简单过滤：只保留 1m 到 30m 范围内的目标
            float distance = (float) Math.sqrt(x * x + y * y + z * z);
            if (distance > 1.0f && distance < 30.0f) {
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
        // 假设摄像头正对雷达 X 轴
        float finalPan = (float) (pan + 180); // 映射到 0-360
        float finalTilt = (float) (tilt); // 映射到 -90 to 90

        if (targetDeviceId != null) {
            drivePTZ(targetDeviceId, targetChannel, finalPan, finalTilt);
        }
    }

    private void drivePTZ(String deviceId, int channel, float pan, float tilt) {
        // 使用 ptzService 封装的接口进行绝对定位，Zoom 默认为 1.0
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
