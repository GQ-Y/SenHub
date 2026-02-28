package com.digital.video.gateway.driver.livox.algorithm;

import com.digital.video.gateway.driver.livox.model.Point;

import java.util.*;

/**
 * DBSCAN聚类算法实现（网格加速版）
 *
 * 使用 3D 网格空间索引将 getNeighbors 从 O(n²) 优化到 O(n)。
 * 每个点根据坐标映射到 (cellSize = eps) 的网格单元，
 * 查找邻居时只扫描当前及相邻的 27 个网格单元。
 */
public class DBSCAN {
    private final float eps;
    private final int minPoints;

    public DBSCAN(float eps, int minPoints) {
        this.eps = eps;
        this.minPoints = minPoints;
    }

    public List<List<Point>> cluster(List<Point> points) {
        if (points == null || points.isEmpty()) {
            return new ArrayList<>();
        }

        final int n = points.size();
        final float cellSize = eps;
        final float epsSq = eps * eps;

        // 建立网格索引：cellKey -> 点索引列表
        Map<Long, List<Integer>> grid = new HashMap<>(n);
        for (int i = 0; i < n; i++) {
            long key = cellKey(points.get(i), cellSize);
            grid.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
        }

        List<List<Point>> clusters = new ArrayList<>();
        int[] labels = new int[n]; // 0=unvisited, -1=noise, >0=clusterId

        int clusterId = 0;
        for (int i = 0; i < n; i++) {
            if (labels[i] != 0) continue;

            List<Integer> neighbors = regionQuery(points, i, grid, cellSize, epsSq);
            if (neighbors.size() < minPoints) {
                labels[i] = -1;
                continue;
            }

            clusterId++;
            labels[i] = clusterId;
            List<Point> cluster = new ArrayList<>();
            cluster.add(points.get(i));

            Deque<Integer> seeds = new ArrayDeque<>(neighbors);
            while (!seeds.isEmpty()) {
                int q = seeds.poll();
                if (labels[q] == -1) {
                    labels[q] = clusterId;
                    cluster.add(points.get(q));
                    continue;
                }
                if (labels[q] != 0) continue;

                labels[q] = clusterId;
                cluster.add(points.get(q));

                List<Integer> qNeighbors = regionQuery(points, q, grid, cellSize, epsSq);
                if (qNeighbors.size() >= minPoints) {
                    seeds.addAll(qNeighbors);
                }
            }

            if (!cluster.isEmpty()) {
                clusters.add(cluster);
            }
        }

        return clusters;
    }

    private List<Integer> regionQuery(List<Point> points, int idx,
                                       Map<Long, List<Integer>> grid, float cellSize, float epsSq) {
        Point p = points.get(idx);
        int cx = cellCoord(p.x, cellSize);
        int cy = cellCoord(p.y, cellSize);
        int cz = cellCoord(p.z, cellSize);

        List<Integer> result = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    long key = packKey(cx + dx, cy + dy, cz + dz);
                    List<Integer> cell = grid.get(key);
                    if (cell == null) continue;
                    for (int j : cell) {
                        if (j == idx) continue;
                        float ddx = p.x - points.get(j).x;
                        float ddy = p.y - points.get(j).y;
                        float ddz = p.z - points.get(j).z;
                        if (ddx * ddx + ddy * ddy + ddz * ddz <= epsSq) {
                            result.add(j);
                        }
                    }
                }
            }
        }
        return result;
    }

    private static int cellCoord(float v, float cellSize) {
        return (int) Math.floor(v / cellSize);
    }

    private static long cellKey(Point p, float cellSize) {
        return packKey(cellCoord(p.x, cellSize), cellCoord(p.y, cellSize), cellCoord(p.z, cellSize));
    }

    private static long packKey(int cx, int cy, int cz) {
        return ((long) (cx + 1_000_000) * 2_000_001L + (cy + 1_000_000)) * 2_000_001L + (cz + 1_000_000);
    }

    public static DBSCAN createDefault() {
        return new DBSCAN(0.3f, 5);
    }
}
