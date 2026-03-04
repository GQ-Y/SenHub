package com.digital.video.gateway.service;

import com.digital.video.gateway.driver.livox.model.Point;
import com.digital.video.gateway.driver.livox.model.BackgroundPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 径向边界网格
 * 用于 O(1) 快速判断点是否在防区内
 * 
 * 原理：将3D空间按球面坐标离散化，预存每个方向上的防区边界距离
 */
public class RadialBoundaryGrid {
    private static final Logger logger = LoggerFactory.getLogger(RadialBoundaryGrid.class);

    // 角度分辨率（度）
    private static final int THETA_RESOLUTION = 360; // 水平角 [0, 360)
    private static final int PHI_RESOLUTION = 180; // 垂直角 [-90, 90] → [0, 180)

    // 边界距离网格 [theta][phi]
    // 值为该方向上的防区边界距离，小于此距离的点视为侵入
    private final float[][] boundaryGrid;

    // 统计信息
    private int validDirections = 0;
    private float avgBoundaryDist = 0;

    public RadialBoundaryGrid() {
        boundaryGrid = new float[THETA_RESOLUTION][PHI_RESOLUTION];
        // 初始化为无穷大（无边界 = 不触发侵入）
        for (int t = 0; t < THETA_RESOLUTION; t++) {
            for (int p = 0; p < PHI_RESOLUTION; p++) {
                boundaryGrid[t][p] = Float.MAX_VALUE;
            }
        }
    }

    /**
     * 从背景点云构建边界网格
     * 
     * @param bgPoints       背景点云
     * @param shrinkDistance 收缩距离（米）
     */
    public void buildFromBackground(List<BackgroundPoint> bgPoints, float shrinkDistance) {
        if (bgPoints == null || bgPoints.isEmpty()) {
            logger.warn("背景点云为空，无法构建边界网格");
            return;
        }

        long startTime = System.currentTimeMillis();

        // 遍历所有背景点，找每个方向上的最近点
        for (BackgroundPoint p : bgPoints) {
            float x = p.getCenterX();
            float y = p.getCenterY();
            float z = p.getCenterZ();

            float r = (float) Math.sqrt(x * x + y * y + z * z);
            if (r < 0.1f)
                continue; // 忽略距雷达太近的点

            int theta = toThetaIndex(x, y);
            int phi = toPhiIndex(z, r);

            // 计算该方向的边界距离 = 背景距离 - 收缩距离
            float boundaryDist = Math.max(0, r - shrinkDistance);

            // 取该方向上的最小值（最近的背景点决定边界）
            if (boundaryDist < boundaryGrid[theta][phi]) {
                boundaryGrid[theta][phi] = boundaryDist;
            }
        }

        // 角度平滑：对每个方向，用周围 3×3 邻域的最小值填充，消除角分辨率间隙导致的泄漏
        float[][] smoothed = new float[THETA_RESOLUTION][PHI_RESOLUTION];
        for (int t = 0; t < THETA_RESOLUTION; t++) {
            for (int p = 0; p < PHI_RESOLUTION; p++) {
                float minVal = boundaryGrid[t][p];
                for (int dt = -1; dt <= 1; dt++) {
                    int nt = (t + dt + THETA_RESOLUTION) % THETA_RESOLUTION;
                    for (int dp = -1; dp <= 1; dp++) {
                        int np = p + dp;
                        if (np < 0 || np >= PHI_RESOLUTION) continue;
                        if (boundaryGrid[nt][np] < minVal) {
                            minVal = boundaryGrid[nt][np];
                        }
                    }
                }
                smoothed[t][p] = minVal;
            }
        }
        for (int t = 0; t < THETA_RESOLUTION; t++) {
            System.arraycopy(smoothed[t], 0, boundaryGrid[t], 0, PHI_RESOLUTION);
        }

        // 统计有效方向数和平均边界距离
        float totalDist = 0;
        validDirections = 0;
        for (int t = 0; t < THETA_RESOLUTION; t++) {
            for (int p = 0; p < PHI_RESOLUTION; p++) {
                if (boundaryGrid[t][p] < Float.MAX_VALUE) {
                    validDirections++;
                    totalDist += boundaryGrid[t][p];
                }
            }
        }
        avgBoundaryDist = validDirections > 0 ? totalDist / validDirections : 0;

        long elapsed = System.currentTimeMillis() - startTime;
        logger.info("边界网格构建完成: 背景点数={}, 有效方向数={}/{}, 平均边界距离={}m, 耗时={}ms",
                bgPoints.size(), validDirections, THETA_RESOLUTION * PHI_RESOLUTION,
                String.format("%.2f", avgBoundaryDist), elapsed);
    }

    /**
     * O(1) 判断点是否侵入防区
     * 
     * @param point 待检测点
     * @return true 如果点在防区边界内（侵入）
     */
    public boolean isIntrusion(Point point) {
        float r = point.distance();
        if (r < 0.3f)
            return false; // 雷达附近噪点忽略（含自身反射、安装件等）

        int theta = toThetaIndex(point.x, point.y);
        int phi = toPhiIndex(point.z, r);

        float boundary = boundaryGrid[theta][phi];

        // 如果该方向无边界（MAX_VALUE），则不触发侵入
        if (boundary >= Float.MAX_VALUE - 1) {
            return false;
        }

        // 点距离 < 边界距离 → 侵入
        return r < boundary;
    }

    /**
     * 计算水平角索引
     * θ = atan2(y, x), 范围 [-π, π] → [0, 360)
     */
    private int toThetaIndex(float x, float y) {
        double theta = Math.atan2(y, x); // [-π, π]
        if (theta < 0)
            theta += 2 * Math.PI; // [0, 2π)
        int index = (int) Math.floor(theta * THETA_RESOLUTION / (2 * Math.PI));
        return Math.max(0, Math.min(THETA_RESOLUTION - 1, index));
    }

    /**
     * 计算垂直角索引
     * φ = asin(z/r), 范围 [-π/2, π/2] → [0, 180)
     */
    private int toPhiIndex(float z, float r) {
        if (r < 0.01f)
            return PHI_RESOLUTION / 2; // 中心点
        double phi = Math.asin(z / r); // [-π/2, π/2]
        phi += Math.PI / 2; // [0, π]
        int index = (int) Math.floor(phi * PHI_RESOLUTION / Math.PI);
        return Math.max(0, Math.min(PHI_RESOLUTION - 1, index));
    }

    /**
     * 获取有效方向数
     */
    public int getValidDirections() {
        return validDirections;
    }

    /**
     * 获取平均边界距离
     */
    public float getAvgBoundaryDist() {
        return avgBoundaryDist;
    }

    /**
     * 检查网格是否已构建
     */
    public boolean isBuilt() {
        return validDirections > 0;
    }
}
