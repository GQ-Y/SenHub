package com.digital.video.gateway.driver.livox.algorithm;

import com.digital.video.gateway.driver.livox.model.Point;

import java.util.*;

/**
 * 点云预处理算法
 */
public class PointCloudProcessor {

    /**
     * 体素下采样
     * 将点云划分为指定分辨率的网格，每个网格只保留一个重心点
     * 
     * @param points 原始点云
     * @param resolution 网格分辨率（米），例如0.05表示5cm
     * @return 下采样后的点云
     */
    public static List<Point> voxelDownsample(List<Point> points, float resolution) {
        if (points == null || points.isEmpty()) {
            return new ArrayList<>();
        }

        // 使用Map存储每个网格的点，key为网格坐标字符串 "x_y_z"
        Map<String, List<Point>> gridMap = new HashMap<>();

        // 将点分配到网格
        for (Point point : points) {
            // 计算网格坐标（整数）
            int gridX = (int) Math.floor(point.x / resolution);
            int gridY = (int) Math.floor(point.y / resolution);
            int gridZ = (int) Math.floor(point.z / resolution);
            String gridKey = gridX + "_" + gridY + "_" + gridZ;

            gridMap.computeIfAbsent(gridKey, k -> new ArrayList<>()).add(point);
        }

        // 对每个网格计算重心点
        List<Point> downsampledPoints = new ArrayList<>();
        for (Map.Entry<String, List<Point>> entry : gridMap.entrySet()) {
            List<Point> gridPoints = entry.getValue();
            if (gridPoints.isEmpty()) {
                continue;
            }

            // 计算重心（平均值）
            float sumX = 0, sumY = 0, sumZ = 0;
            for (Point p : gridPoints) {
                sumX += p.x;
                sumY += p.y;
                sumZ += p.z;
            }
            int count = gridPoints.size();
            Point centroid = new Point(sumX / count, sumY / count, sumZ / count);
            downsampledPoints.add(centroid);
        }

        return downsampledPoints;
    }

    /**
     * 统计去噪（Statistical Outlier Removal）
     * 移除距离邻居平均距离过大的点（噪声点）
     * 
     * @param points 原始点云
     * @param kNeighbors 邻居数量（默认20）
     * @param stdDevThreshold 标准差倍数阈值（默认1.0）
     * @return 去噪后的点云
     */
    public static List<Point> statisticalOutlierRemoval(List<Point> points, int kNeighbors, float stdDevThreshold) {
        if (points == null || points.isEmpty() || points.size() < kNeighbors) {
            return new ArrayList<>(points);
        }

        // 计算每个点到其k个最近邻的平均距离
        List<Float> meanDistances = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            Point point = points.get(i);
            List<Float> distances = new ArrayList<>();

            // 找到k个最近邻
            for (int j = 0; j < points.size(); j++) {
                if (i == j) continue;
                float dist = point.distanceTo(points.get(j));
                distances.add(dist);
            }

            // 排序并取前k个
            distances.sort(Float::compareTo);
            float sum = 0;
            int count = Math.min(kNeighbors, distances.size());
            for (int k = 0; k < count; k++) {
                sum += distances.get(k);
            }
            meanDistances.add(sum / count);
        }

        // 计算全局平均值和标准差
        float globalMean = 0;
        for (float dist : meanDistances) {
            globalMean += dist;
        }
        globalMean /= meanDistances.size();

        float variance = 0;
        for (float dist : meanDistances) {
            float diff = dist - globalMean;
            variance += diff * diff;
        }
        float stdDev = (float) Math.sqrt(variance / meanDistances.size());

        // 过滤噪声点
        float threshold = globalMean + stdDevThreshold * stdDev;
        List<Point> filteredPoints = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            if (meanDistances.get(i) <= threshold) {
                filteredPoints.add(points.get(i));
            }
        }

        return filteredPoints;
    }

    /**
     * 统计去噪（使用默认参数）
     */
    public static List<Point> statisticalOutlierRemoval(List<Point> points) {
        return statisticalOutlierRemoval(points, 20, 1.0f);
    }

    /**
     * 距离过滤
     * 只保留指定距离范围内的点
     * 
     * @param points 原始点云
     * @param minDistance 最小距离（米）
     * @param maxDistance 最大距离（米）
     * @return 过滤后的点云
     */
    public static List<Point> distanceFilter(List<Point> points, float minDistance, float maxDistance) {
        if (points == null || points.isEmpty()) {
            return new ArrayList<>();
        }

        List<Point> filteredPoints = new ArrayList<>();
        for (Point point : points) {
            float distance = point.distance();
            if (distance >= minDistance && distance <= maxDistance) {
                filteredPoints.add(point);
            }
        }
        return filteredPoints;
    }
}
