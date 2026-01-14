package com.digital.video.gateway.driver.livox.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 背景模型数据模型
 * 包含处理后的背景点云和空间索引
 */
public class BackgroundModel {
    private String backgroundId;
    private String deviceId;
    private float gridResolution; // 网格分辨率（米）
    private List<BackgroundPoint> points; // 背景点列表
    private Map<String, BackgroundPoint> gridIndex; // 空间哈希索引：gridKey -> BackgroundPoint

    public BackgroundModel() {
        this.points = new ArrayList<>();
        this.gridIndex = new HashMap<>();
    }

    public BackgroundModel(String backgroundId, String deviceId, float gridResolution) {
        this.backgroundId = backgroundId;
        this.deviceId = deviceId;
        this.gridResolution = gridResolution;
        this.points = new ArrayList<>();
        this.gridIndex = new HashMap<>();
    }

    /**
     * 添加背景点
     */
    public void addPoint(BackgroundPoint point) {
        points.add(point);
        if (point.getGridKey() != null) {
            gridIndex.put(point.getGridKey(), point);
        }
    }

    /**
     * 根据网格键查找背景点
     */
    public BackgroundPoint getPointByGridKey(String gridKey) {
        return gridIndex.get(gridKey);
    }

    /**
     * 检查网格键是否存在
     */
    public boolean containsGridKey(String gridKey) {
        return gridIndex.containsKey(gridKey);
    }

    public String getBackgroundId() {
        return backgroundId;
    }

    public void setBackgroundId(String backgroundId) {
        this.backgroundId = backgroundId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public float getGridResolution() {
        return gridResolution;
    }

    public void setGridResolution(float gridResolution) {
        this.gridResolution = gridResolution;
    }

    public List<BackgroundPoint> getPoints() {
        return points;
    }

    public void setPoints(List<BackgroundPoint> points) {
        this.points = points != null ? points : new ArrayList<>();
        // 重建索引
        gridIndex.clear();
        for (BackgroundPoint point : this.points) {
            if (point.getGridKey() != null) {
                gridIndex.put(point.getGridKey(), point);
            }
        }
    }

    public int getPointCount() {
        return points != null ? points.size() : 0;
    }
}
