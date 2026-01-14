package com.digital.video.gateway.driver.livox.algorithm;

import com.digital.video.gateway.driver.livox.model.Point;

/**
 * 坐标系转换工具类
 * 将雷达坐标系转换为摄像头坐标系
 */
public class CoordinateTransform {
    // 平移参数（米）
    private float translationX = 0;
    private float translationY = 0;
    private float translationZ = 0;

    // 旋转参数（欧拉角，度）
    private float rotationX = 0;
    private float rotationY = 0;
    private float rotationZ = 0;

    // 缩放参数
    private float scale = 1.0f;

    public CoordinateTransform() {
    }

    public CoordinateTransform(float translationX, float translationY, float translationZ,
                               float rotationX, float rotationY, float rotationZ, float scale) {
        this.translationX = translationX;
        this.translationY = translationY;
        this.translationZ = translationZ;
        this.rotationX = rotationX;
        this.rotationY = rotationY;
        this.rotationZ = rotationZ;
        this.scale = scale;
    }

    /**
     * 将雷达坐标转换为摄像头坐标
     * 
     * @param radarPoint 雷达坐标系中的点
     * @return 摄像头坐标系中的点
     */
    public Point transformRadarToCamera(Point radarPoint) {
        // 1. 应用缩放
        float x = radarPoint.x * scale;
        float y = radarPoint.y * scale;
        float z = radarPoint.z * scale;

        // 2. 应用旋转（绕Z轴、Y轴、X轴顺序）
        // 转换为弧度
        double rx = Math.toRadians(rotationX);
        double ry = Math.toRadians(rotationY);
        double rz = Math.toRadians(rotationZ);

        // 绕Z轴旋转
        double cosZ = Math.cos(rz);
        double sinZ = Math.sin(rz);
        double x1 = x * cosZ - y * sinZ;
        double y1 = x * sinZ + y * cosZ;
        double z1 = z;

        // 绕Y轴旋转
        double cosY = Math.cos(ry);
        double sinY = Math.sin(ry);
        double x2 = x1 * cosY + z1 * sinY;
        double y2 = y1;
        double z2 = -x1 * sinY + z1 * cosY;

        // 绕X轴旋转
        double cosX = Math.cos(rx);
        double sinX = Math.sin(rx);
        double x3 = x2;
        double y3 = y2 * cosX - z2 * sinX;
        double z3 = y2 * sinX + z2 * cosX;

        // 3. 应用平移
        float cameraX = (float) (x3 + translationX);
        float cameraY = (float) (y3 + translationY);
        float cameraZ = (float) (z3 + translationZ);

        return new Point(cameraX, cameraY, cameraZ, radarPoint.reflectivity);
    }

    /**
     * 计算PTZ角度（水平角和垂直角）
     * 基于摄像头坐标系中的点
     * 
     * @param cameraPoint 摄像头坐标系中的点
     * @return [pan, tilt] 角度数组（度）
     */
    public float[] calculatePTZAngles(Point cameraPoint) {
        // Pan: 水平角，atan2(y, x)
        double pan = Math.toDegrees(Math.atan2(cameraPoint.y, cameraPoint.x));
        
        // Tilt: 垂直角，atan2(z, sqrt(x^2 + y^2))
        double horizontalDistance = Math.sqrt(cameraPoint.x * cameraPoint.x + cameraPoint.y * cameraPoint.y);
        double tilt = Math.toDegrees(Math.atan2(cameraPoint.z, horizontalDistance));

        // 映射到0-360度范围
        pan = (pan + 360) % 360;
        
        return new float[]{(float) pan, (float) tilt};
    }

    // Getters and Setters
    public float getTranslationX() { return translationX; }
    public void setTranslationX(float translationX) { this.translationX = translationX; }

    public float getTranslationY() { return translationY; }
    public void setTranslationY(float translationY) { this.translationY = translationY; }

    public float getTranslationZ() { return translationZ; }
    public void setTranslationZ(float translationZ) { this.translationZ = translationZ; }

    public float getRotationX() { return rotationX; }
    public void setRotationX(float rotationX) { this.rotationX = rotationX; }

    public float getRotationY() { return rotationY; }
    public void setRotationY(float rotationY) { this.rotationY = rotationY; }

    public float getRotationZ() { return rotationZ; }
    public void setRotationZ(float rotationZ) { this.rotationZ = rotationZ; }

    public float getScale() { return scale; }
    public void setScale(float scale) { this.scale = scale; }
}
