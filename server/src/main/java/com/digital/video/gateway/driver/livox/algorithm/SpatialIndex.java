package com.digital.video.gateway.driver.livox.algorithm;

import com.digital.video.gateway.driver.livox.model.BackgroundPoint;
import com.digital.video.gateway.driver.livox.model.Point;

import java.util.*;

/**
 * 空间索引（使用空间哈希表）
 * 用于快速查找最近邻点
 */
public class SpatialIndex {
    private final float resolution; // 网格分辨率
    private final Map<String, List<BackgroundPoint>> gridIndex; // 网格索引

    public SpatialIndex(float resolution) {
        this.resolution = resolution;
        this.gridIndex = new HashMap<>();
    }

    /**
     * 添加背景点到索引
     */
    public void addPoint(BackgroundPoint point) {
        String gridKey = point.getGridKey();
        if (gridKey != null) {
            gridIndex.computeIfAbsent(gridKey, k -> new ArrayList<>()).add(point);
        }
    }

    /**
     * 批量添加背景点
     */
    public void addPoints(List<BackgroundPoint> points) {
        for (BackgroundPoint point : points) {
            addPoint(point);
        }
    }

    /**
     * 查找指定半径内的最近邻点
     * 
     * @param queryPoint 查询点
     * @param radius 搜索半径（米）
     * @return 最近邻点列表
     */
    public List<BackgroundPoint> findNearestNeighbors(Point queryPoint, float radius) {
        List<BackgroundPoint> neighbors = new ArrayList<>();

        // 计算需要搜索的网格范围
        int gridRadius = (int) Math.ceil(radius / resolution);
        int gridX = (int) Math.floor(queryPoint.x / resolution);
        int gridY = (int) Math.floor(queryPoint.y / resolution);
        int gridZ = (int) Math.floor(queryPoint.z / resolution);

        // 在周围网格中搜索
        for (int dx = -gridRadius; dx <= gridRadius; dx++) {
            for (int dy = -gridRadius; dy <= gridRadius; dy++) {
                for (int dz = -gridRadius; dz <= gridRadius; dz++) {
                    String gridKey = (gridX + dx) + "_" + (gridY + dy) + "_" + (gridZ + dz);
                    List<BackgroundPoint> gridPoints = gridIndex.get(gridKey);
                    if (gridPoints != null) {
                        for (BackgroundPoint bgPoint : gridPoints) {
                            Point bgPointObj = bgPoint.toPoint();
                            float distance = queryPoint.distanceTo(bgPointObj);
                            if (distance <= radius) {
                                neighbors.add(bgPoint);
                            }
                        }
                    }
                }
            }
        }

        return neighbors;
    }

    /**
     * 查找最近的背景点（在指定半径内）
     * 
     * @param queryPoint 查询点
     * @param radius 搜索半径（米）
     * @return 最近的背景点，如果未找到则返回null
     */
    public BackgroundPoint findNearest(Point queryPoint, float radius) {
        List<BackgroundPoint> neighbors = findNearestNeighbors(queryPoint, radius);
        if (neighbors.isEmpty()) {
            return null;
        }

        // 找到距离最近的点
        BackgroundPoint nearest = null;
        float minDistance = Float.MAX_VALUE;
        for (BackgroundPoint bgPoint : neighbors) {
            Point bgPointObj = bgPoint.toPoint();
            float distance = queryPoint.distanceTo(bgPointObj);
            if (distance < minDistance) {
                minDistance = distance;
                nearest = bgPoint;
            }
        }

        return nearest;
    }

    /**
     * 检查点是否在背景中（在指定半径内存在背景点）
     * 
     * @param queryPoint 查询点
     * @param radius 容差半径（米），默认0.1米（10cm）
     * @return 如果存在背景点则返回true
     */
    public boolean isPointInBackground(Point queryPoint, float radius) {
        return findNearest(queryPoint, radius) != null;
    }

    /**
     * 检查点是否在背景中（使用默认容差0.1米）
     */
    public boolean isPointInBackground(Point queryPoint) {
        return isPointInBackground(queryPoint, 0.1f);
    }

    /**
     * 获取网格键
     */
    public static String getGridKey(Point point, float resolution) {
        int gridX = (int) Math.floor(point.x / resolution);
        int gridY = (int) Math.floor(point.y / resolution);
        int gridZ = (int) Math.floor(point.z / resolution);
        return gridX + "_" + gridY + "_" + gridZ;
    }

    /**
     * 清空索引
     */
    public void clear() {
        gridIndex.clear();
    }

    /**
     * 获取索引中的点数量
     */
    public int size() {
        int count = 0;
        for (List<BackgroundPoint> points : gridIndex.values()) {
            count += points.size();
        }
        return count;
    }
}
