package com.digital.video.gateway.driver.livox.algorithm;

import com.digital.video.gateway.driver.livox.model.Point;

import java.util.*;

/**
 * DBSCAN聚类算法实现
 * 用于对点云进行聚类，识别独立的物体
 */
public class DBSCAN {
    private final float eps; // 邻域半径
    private final int minPoints; // 最小点数

    public DBSCAN(float eps, int minPoints) {
        this.eps = eps;
        this.minPoints = minPoints;
    }

    /**
     * 对点云进行聚类
     * 
     * @param points 输入点云
     * @return 聚类结果，每个List<Point>代表一个聚类
     */
    public List<List<Point>> cluster(List<Point> points) {
        if (points == null || points.isEmpty()) {
            return new ArrayList<>();
        }

        List<List<Point>> clusters = new ArrayList<>();
        Set<Integer> visited = new HashSet<>();
        Set<Integer> noise = new HashSet<>();

        for (int i = 0; i < points.size(); i++) {
            if (visited.contains(i)) {
                continue;
            }

            visited.add(i);
            List<Integer> neighbors = getNeighbors(points, i);

            if (neighbors.size() < minPoints) {
                noise.add(i);
                continue;
            }

            // 创建新聚类
            List<Point> cluster = new ArrayList<>();
            expandCluster(points, i, neighbors, cluster, visited, noise);
            if (!cluster.isEmpty()) {
                clusters.add(cluster);
            }
        }

        return clusters;
    }

    /**
     * 扩展聚类
     */
    private void expandCluster(List<Point> points, int pointIndex, List<Integer> neighbors,
                               List<Point> cluster, Set<Integer> visited, Set<Integer> noise) {
        cluster.add(points.get(pointIndex));

        Queue<Integer> seeds = new LinkedList<>(neighbors);

        while (!seeds.isEmpty()) {
            int currentIndex = seeds.poll();

            if (noise.contains(currentIndex)) {
                noise.remove(currentIndex);
                cluster.add(points.get(currentIndex));
                continue;
            }

            if (visited.contains(currentIndex)) {
                continue;
            }

            visited.add(currentIndex);
            cluster.add(points.get(currentIndex));

            List<Integer> currentNeighbors = getNeighbors(points, currentIndex);
            if (currentNeighbors.size() >= minPoints) {
                seeds.addAll(currentNeighbors);
            }
        }
    }

    /**
     * 获取点的邻居（在eps半径内）
     */
    private List<Integer> getNeighbors(List<Point> points, int pointIndex) {
        List<Integer> neighbors = new ArrayList<>();
        Point point = points.get(pointIndex);

        for (int i = 0; i < points.size(); i++) {
            if (i == pointIndex) {
                continue;
            }
            float distance = point.distanceTo(points.get(i));
            if (distance <= eps) {
                neighbors.add(i);
            }
        }

        return neighbors;
    }

    /**
     * 使用默认参数创建DBSCAN实例
     * eps=0.3m, minPoints=5
     */
    public static DBSCAN createDefault() {
        return new DBSCAN(0.3f, 5);
    }
}
