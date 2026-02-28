package com.digital.video.gateway.driver.livox.algorithm;

import com.digital.video.gateway.driver.livox.model.PointCluster;
import com.digital.video.gateway.driver.livox.model.TargetType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于点云聚类几何特征的目标分类器。
 * 使用 bbox 高度(Z跨度)、宽度、深度、体积、点数等特征
 * 将目标分类为：人、动物、车辆、其他。
 */
public class TargetClassifier {

    private static final Logger log = LoggerFactory.getLogger(TargetClassifier.class);

    // ---- 人 ----
    private static final float PERSON_HEIGHT_MIN = 0.3f;
    private static final float PERSON_HEIGHT_MAX = 2.2f;
    private static final float PERSON_WIDTH_MAX = 1.5f;
    private static final float PERSON_DEPTH_MAX = 1.5f;
    private static final float PERSON_VOLUME_MAX = 3.0f;
    private static final int PERSON_POINT_COUNT_MIN = 5;

    // ---- 车辆 ----
    private static final float VEHICLE_VOLUME_MIN = 2.0f;
    private static final float VEHICLE_LENGTH_MIN = 2.0f;
    private static final float VEHICLE_HEIGHT_MAX = 3.5f;

    // ---- 动物 ----
    private static final float ANIMAL_HEIGHT_MAX = 0.8f;
    private static final float ANIMAL_VOLUME_MAX = 1.5f;
    private static final int ANIMAL_POINT_COUNT_MAX = 100;

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

        if (isVehicle(height, width, depth, volume, horizontalSpan)) {
            log.debug("分类为车辆: height={}, width={}, depth={}, volume={}, points={}",
                    height, width, depth, volume, pointCount);
            return TargetType.VEHICLE;
        }

        if (isPerson(height, width, depth, volume, pointCount)) {
            log.debug("分类为人: height={}, width={}, depth={}, volume={}, points={}",
                    height, width, depth, volume, pointCount);
            return TargetType.PERSON;
        }

        if (isAnimal(height, volume, pointCount)) {
            log.debug("分类为动物: height={}, volume={}, points={}", height, volume, pointCount);
            return TargetType.ANIMAL;
        }

        log.debug("分类为其他: height={}, width={}, depth={}, volume={}, points={}",
                height, width, depth, volume, pointCount);
        return TargetType.OTHER;
    }

    private boolean isPerson(float height, float width, float depth, float volume, int pointCount) {
        return height >= PERSON_HEIGHT_MIN
                && height <= PERSON_HEIGHT_MAX
                && width <= PERSON_WIDTH_MAX
                && depth <= PERSON_DEPTH_MAX
                && volume <= PERSON_VOLUME_MAX
                && pointCount >= PERSON_POINT_COUNT_MIN;
    }

    private boolean isVehicle(float height, float width, float depth, float volume, float horizontalSpan) {
        return (volume >= VEHICLE_VOLUME_MIN || horizontalSpan >= VEHICLE_LENGTH_MIN)
                && height <= VEHICLE_HEIGHT_MAX;
    }

    private boolean isAnimal(float height, float volume, int pointCount) {
        return height > 0
                && height <= ANIMAL_HEIGHT_MAX
                && volume <= ANIMAL_VOLUME_MAX
                && pointCount <= ANIMAL_POINT_COUNT_MAX;
    }
}
