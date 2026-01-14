package com.digital.video.gateway.driver.livox.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 点云聚类数据模型
 */
public class PointCluster {
    private String clusterId;
    private List<Point> points; // 聚类中的点
    private Point centroid; // 质心
    private float volume; // 体积（立方米）
    private BoundingBox bbox; // 边界框

    public PointCluster() {
        this.points = new ArrayList<>();
    }

    public PointCluster(String clusterId, List<Point> points) {
        this.clusterId = clusterId;
        this.points = points != null ? points : new ArrayList<>();
        calculateFeatures();
    }

    /**
     * 计算聚类特征（质心、体积、边界框）
     */
    public void calculateFeatures() {
        if (points == null || points.isEmpty()) {
            centroid = new Point(0, 0, 0);
            volume = 0;
            bbox = new BoundingBox();
            return;
        }

        // 计算质心
        float sumX = 0, sumY = 0, sumZ = 0;
        float minX = Float.MAX_VALUE, maxX = Float.MIN_VALUE;
        float minY = Float.MAX_VALUE, maxY = Float.MIN_VALUE;
        float minZ = Float.MAX_VALUE, maxZ = Float.MIN_VALUE;

        for (Point p : points) {
            sumX += p.x;
            sumY += p.y;
            sumZ += p.z;
            minX = Math.min(minX, p.x);
            maxX = Math.max(maxX, p.x);
            minY = Math.min(minY, p.y);
            maxY = Math.max(maxY, p.y);
            minZ = Math.min(minZ, p.z);
            maxZ = Math.max(maxZ, p.z);
        }

        int count = points.size();
        centroid = new Point(sumX / count, sumY / count, sumZ / count);

        // 计算边界框
        bbox = new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);

        // 估算体积（使用边界框体积）
        volume = (maxX - minX) * (maxY - minY) * (maxZ - minZ);
    }

    public String getClusterId() {
        return clusterId;
    }

    public void setClusterId(String clusterId) {
        this.clusterId = clusterId;
    }

    public List<Point> getPoints() {
        return points;
    }

    public void setPoints(List<Point> points) {
        this.points = points != null ? points : new ArrayList<>();
        calculateFeatures();
    }

    public Point getCentroid() {
        return centroid;
    }

    public float getVolume() {
        return volume;
    }

    public BoundingBox getBbox() {
        return bbox;
    }

    public int getPointCount() {
        return points != null ? points.size() : 0;
    }

    /**
     * 边界框数据模型
     */
    public static class BoundingBox {
        public float minX, minY, minZ;
        public float maxX, maxY, maxZ;

        public BoundingBox() {
        }

        public BoundingBox(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }
    }
}
