package com.digital.video.gateway.driver.livox.model;

/**
 * 点云点数据模型
 */
public class Point {
    public float x;
    public float y;
    public float z;
    public byte reflectivity; // 反射强度

    public Point() {
    }

    public Point(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Point(float x, float y, float z, byte reflectivity) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.reflectivity = reflectivity;
    }

    // 标记该点所属的防区ID（如果是侵入点）
    public String zoneId;

    /**
     * 计算到原点的距离
     */
    public float distance() {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    /**
     * 计算到另一个点的距离
     */
    public float distanceTo(Point other) {
        float dx = x - other.x;
        float dy = y - other.y;
        float dz = z - other.z;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Override
    public String toString() {
        return String.format("Point(%.3f, %.3f, %.3f)", x, y, z);
    }
}
