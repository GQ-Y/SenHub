package com.digital.video.gateway.driver.livox.algorithm;

import com.digital.video.gateway.driver.livox.model.PointCluster;
import com.digital.video.gateway.driver.livox.model.TargetType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于点云聚类几何特征的目标分类器。
 *
 * 分类优先级（从高到低）：ANIMAL → PERSON → VEHICLE → OTHER
 *
 * 关键设计考量（Livox Mid-360 稀疏点云 + 800ms 累积窗口）：
 * - 每帧仅 1-3 个前景点，累积后 3-15 点
 * - 人移动时累积点在水平面扩散，bbox 宽度/深度可达 3-5m
 * - 累积聚类的体积和水平跨度对于人/车都可能很大，无法作为可靠区分
 * - 高度（Z 跨度）是最可靠的特征：有一定 Z 跨度的大概率是人（站立状态）
 * - 安防场景默认假设：除非有强烈的反面证据，否则入侵目标视为人
 */
public class TargetClassifier {

    private static final Logger log = LoggerFactory.getLogger(TargetClassifier.class);

    // ---- 动物（贴地、矮小、优先排除）----
    private static final float ANIMAL_HEIGHT_MAX = 0.4f;

    // ---- 人（核心判据：有一定 Z 跨度即可）----
    private static final float PERSON_HEIGHT_MIN = 0.15f;
    private static final float PERSON_HEIGHT_MAX = 3.0f;

    // ---- 车辆（极端大型目标，几乎不可能被人触发）----
    private static final float VEHICLE_VOLUME_MIN = 30.0f;
    private static final float VEHICLE_LENGTH_MIN = 4.0f;
    private static final int VEHICLE_POINT_COUNT_MIN = 20;

    public TargetType classify(PointCluster cluster) {
        if (cluster == null || cluster.getBbox() == null || cluster.getPointCount() == 0) {
            return TargetType.OTHER;
        }

        float height = cluster.getHeight();
        float width = cluster.getWidth();
        float depth = cluster.getDepth();
        float volume = cluster.getVolume();
        int pointCount = cluster.getPointCount();
        float horizontalSpan = Math.max(width, depth);

        log.debug("分类输入: height={}, width={}, depth={}, volume={}, span={}, points={}",
                height, width, depth, volume, horizontalSpan, pointCount);

        // 1. 动物：非常矮小的贴地目标
        if (height > 0 && height <= ANIMAL_HEIGHT_MAX && horizontalSpan < 2.0f) {
            log.debug("分类为动物: height={}, span={}, points={}", height, horizontalSpan, pointCount);
            return TargetType.ANIMAL;
        }

        // 2. 人：有一定高度即为人（安防场景的核心假设）
        if (height >= PERSON_HEIGHT_MIN && height <= PERSON_HEIGHT_MAX) {
            log.debug("分类为人: height={}, points={}", height, pointCount);
            return TargetType.PERSON;
        }

        // 3. 车辆：极大型目标（体积、跨度、点数同时满足极高门槛）
        if (volume >= VEHICLE_VOLUME_MIN
                && horizontalSpan >= VEHICLE_LENGTH_MIN
                && pointCount >= VEHICLE_POINT_COUNT_MIN) {
            log.debug("分类为车辆: height={}, volume={}, span={}, points={}",
                    height, volume, horizontalSpan, pointCount);
            return TargetType.VEHICLE;
        }

        log.debug("分类为其他: height={}, volume={}, span={}, points={}",
                height, volume, horizontalSpan, pointCount);
        return TargetType.OTHER;
    }
}
