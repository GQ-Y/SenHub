package com.digital.video.gateway.driver.livox.model;

/**
 * 背景点数据模型
 * 表示体素网格中的背景点
 */
public class BackgroundPoint {
    private String gridKey; // 网格索引键，格式: "x_y_z"（整数坐标）
    private float centerX; // 网格中心x坐标（米）
    private float centerY;
    private float centerZ;
    private int pointCount; // 该网格内的原始点数
    private float meanDistance; // 平均距离
    private float stdDeviation; // 标准差（用于噪声过滤）

    public BackgroundPoint() {
    }

    public BackgroundPoint(String gridKey, float centerX, float centerY, float centerZ, int pointCount) {
        this.gridKey = gridKey;
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.pointCount = pointCount;
    }

    /**
     * 转换为Point对象
     */
    public Point toPoint() {
        return new Point(centerX, centerY, centerZ);
    }

    public String getGridKey() {
        return gridKey;
    }

    public void setGridKey(String gridKey) {
        this.gridKey = gridKey;
    }

    public float getCenterX() {
        return centerX;
    }

    public void setCenterX(float centerX) {
        this.centerX = centerX;
    }

    public float getCenterY() {
        return centerY;
    }

    public void setCenterY(float centerY) {
        this.centerY = centerY;
    }

    public float getCenterZ() {
        return centerZ;
    }

    public void setCenterZ(float centerZ) {
        this.centerZ = centerZ;
    }

    public int getPointCount() {
        return pointCount;
    }

    public void setPointCount(int pointCount) {
        this.pointCount = pointCount;
    }

    public float getMeanDistance() {
        return meanDistance;
    }

    public void setMeanDistance(float meanDistance) {
        this.meanDistance = meanDistance;
    }

    public float getStdDeviation() {
        return stdDeviation;
    }

    public void setStdDeviation(float stdDeviation) {
        this.stdDeviation = stdDeviation;
    }
}
