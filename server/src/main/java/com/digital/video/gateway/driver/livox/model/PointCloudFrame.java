package com.digital.video.gateway.driver.livox.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 点云帧数据模型
 */
public class PointCloudFrame {
    private long timestamp; // 时间戳（毫秒）
    private List<Point> points; // 点列表
    private int frameIndex; // 帧序号

    public PointCloudFrame() {
        this.timestamp = System.currentTimeMillis();
        this.points = new ArrayList<>();
    }

    public PointCloudFrame(long timestamp, List<Point> points) {
        this.timestamp = timestamp;
        this.points = points != null ? points : new ArrayList<>();
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public List<Point> getPoints() {
        return points;
    }

    public void setPoints(List<Point> points) {
        this.points = points != null ? points : new ArrayList<>();
    }

    public void addPoint(Point point) {
        if (points == null) {
            points = new ArrayList<>();
        }
        points.add(point);
    }

    public int getPointCount() {
        return points != null ? points.size() : 0;
    }

    public int getFrameIndex() {
        return frameIndex;
    }

    public void setFrameIndex(int frameIndex) {
        this.frameIndex = frameIndex;
    }
}
