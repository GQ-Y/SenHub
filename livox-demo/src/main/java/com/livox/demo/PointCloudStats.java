package com.livox.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 点云数据统计类
 * 解析笛卡尔坐标点云（Type 0x01）并计算统计信息
 */
public class PointCloudStats {

    private static final Logger log = LoggerFactory.getLogger(PointCloudStats.class);
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    // 点云数据类型
    private static final byte DATA_TYPE_CARTESIAN = 0x01;

    // 统计信息
    public long totalFrames = 0;
    public long totalPoints = 0;

    /**
     * 解析点云数据（从 JNI 回调）
     */
    public FrameStats parsePointCloudData(int handle, int devType, int pointCount, 
                                          int dataType, byte[] data) {
        if (data == null || data.length == 0 || pointCount == 0) {
            return null;
        }

        // 只处理笛卡尔坐标高精度数据 (0x01)
        if (dataType != 0x01) {
            log.debug("跳过非笛卡尔坐标高精度数据: Type={}", dataType);
            return null;
        }

        // 解析点云数据
        // 每个点: x(4), y(4), z(4), reflectivity(1), tag(1) = 14 字节
        return parseCartesianHighData(data, pointCount);
    }

    /**
     * 解析笛卡尔坐标高精度数据
     */
    private FrameStats parseCartesianHighData(byte[] data, int pointCount) {
        List<Point> points = new ArrayList<>();
        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        // 每个点 14 字节: x(4), y(4), z(4), reflectivity(1), tag(1)
        int pointSize = 14;
        int maxPoints = Math.min(pointCount, data.length / pointSize);

        for (int i = 0; i < maxPoints; i++) {
            int xInt = bb.getInt();
            int yInt = bb.getInt();
            int zInt = bb.getInt();
            byte reflectivity = bb.get();
            bb.get(); // tag

            // 转换为米（单位是毫米）
            float x = xInt / 1000.0f;
            float y = yInt / 1000.0f;
            float z = zInt / 1000.0f;

            points.add(new Point(x, y, z, reflectivity));
        }

        if (points.isEmpty()) {
            return null;
        }

        // 计算统计信息
        FrameStats stats = calculateStats(points);
        totalFrames++;
        totalPoints += stats.pointCount;

        return stats;
    }


    /**
     * 计算点云统计信息
     */
    private FrameStats calculateStats(List<Point> points) {
        FrameStats stats = new FrameStats();
        stats.frameNumber = totalFrames + 1;
        stats.pointCount = points.size();
        stats.timestamp = new Date();

        // 初始化范围
        float minX = Float.MAX_VALUE, maxX = Float.MIN_VALUE;
        float minY = Float.MAX_VALUE, maxY = Float.MIN_VALUE;
        float minZ = Float.MAX_VALUE, maxZ = Float.MIN_VALUE;

        float sumDistance = 0;
        float sumX = 0, sumY = 0, sumZ = 0;

        for (Point p : points) {
            // 更新范围
            minX = Math.min(minX, p.x);
            maxX = Math.max(maxX, p.x);
            minY = Math.min(minY, p.y);
            maxY = Math.max(maxY, p.y);
            minZ = Math.min(minZ, p.z);
            maxZ = Math.max(maxZ, p.z);

            // 计算距离（到原点的距离）
            float distance = (float) Math.sqrt(p.x * p.x + p.y * p.y + p.z * p.z);
            sumDistance += distance;

            // 累加坐标
            sumX += p.x;
            sumY += p.y;
            sumZ += p.z;
        }

        stats.minX = minX;
        stats.maxX = maxX;
        stats.minY = minY;
        stats.maxY = maxY;
        stats.minZ = minZ;
        stats.maxZ = maxZ;

        stats.avgDistance = sumDistance / points.size();
        stats.centroidX = sumX / points.size();
        stats.centroidY = sumY / points.size();
        stats.centroidZ = sumZ / points.size();

        // 计算点云密度（近似：点数 / 覆盖面积）
        float xRange = maxX - minX;
        float yRange = maxY - minY;
        float area = xRange * yRange;
        if (area > 0) {
            stats.density = points.size() / area; // 点/平方米
        } else {
            stats.density = 0;
        }

        return stats;
    }

    /**
     * 格式化输出统计信息到控制台
     */
    public void printStats(FrameStats stats) {
        if (stats == null) {
            return;
        }

        String timestamp = dateFormat.format(stats.timestamp);
        System.out.println(String.format(
            "[%s] 收到点云数据帧 #%d",
            timestamp, stats.frameNumber
        ));
        System.out.println(String.format(
            "  点数: %d",
            stats.pointCount
        ));
        System.out.println(String.format(
            "  坐标范围: X[%.2f, %.2f] Y[%.2f, %.2f] Z[%.2f, %.2f] (米)",
            stats.minX, stats.maxX, stats.minY, stats.maxY, stats.minZ, stats.maxZ
        ));
        System.out.println(String.format(
            "  质心位置: (%.2f, %.2f, %.2f) (米)",
            stats.centroidX, stats.centroidY, stats.centroidZ
        ));
        System.out.println(String.format(
            "  平均距离: %.2f 米",
            stats.avgDistance
        ));
        System.out.println(String.format(
            "  点云密度: %.0f 点/平方米",
            stats.density
        ));
        System.out.println(String.format(
            "  累计帧数: %d, 累计点数: %d",
            totalFrames, totalPoints
        ));
        System.out.println();
    }

    /**
     * 点云数据点
     */
    private static class Point {
        float x, y, z;
        byte reflectivity;

        Point(float x, float y, float z, byte reflectivity) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.reflectivity = reflectivity;
        }
    }

    /**
     * 帧统计信息
     */
    public static class FrameStats {
        public long frameNumber;
        public int pointCount;
        public Date timestamp;

        // 坐标范围
        public float minX, maxX;
        public float minY, maxY;
        public float minZ, maxZ;

        // 质心
        public float centroidX, centroidY, centroidZ;

        // 统计值
        public float avgDistance;
        public float density;
    }

    /**
     * 获取累计统计信息
     */
    public String getSummary() {
        return String.format("累计接收: %d 帧, %d 点", totalFrames, totalPoints);
    }
}
