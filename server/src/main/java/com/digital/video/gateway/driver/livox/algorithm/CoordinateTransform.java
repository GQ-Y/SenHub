package com.digital.video.gateway.driver.livox.algorithm;

import com.digital.video.gateway.driver.livox.model.Point;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 坐标系转换工具类
 * 将雷达坐标系转换为摄像头坐标系
 */
public class CoordinateTransform {
    // 平移参数（米）
    public float translationX = 0;
    public float translationY = 0;
    public float translationZ = 0;

    // 旋转参数（欧拉角，度）
    public float rotationX = 0;
    public float rotationY = 0;
    public float rotationZ = 0;

    // 缩放参数
    public float scale = 1.0f;

    // 距离-变倍标定数据
    private final List<float[]> zoomCalibPoints = new ArrayList<>();

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

    /**
     * 添加一个距离-变倍标定数据点
     */
    public void addZoomCalibPoint(float distance, float zoom) {
        zoomCalibPoints.add(new float[]{distance, zoom});
    }

    public boolean hasZoomCalibration() {
        return !zoomCalibPoints.isEmpty();
    }

    /**
     * 根据标定数据估算指定距离的变倍倍数。
     *
     * 算法设计原理：目标在摄像头画面中的角大小与距离成反比，因此保持相同
     * 画面大小所需的变倍与距离成正比: zoom = z₀ * (d / d₀)。
     *
     * 单点标定：以标定点的 zoom/distance 比例进行线性推算，但施加合理的距离分段上限，
     * 避免近距离噪点导致变倍飙升。
     * 多点标定：在标定点间做线性插值，超出范围时使用最近端点值。
     */
    public float estimateZoom(float distance) {
        if (zoomCalibPoints.isEmpty()) return 1.0f;
        if (distance <= 0) return 1.0f;

        if (zoomCalibPoints.size() == 1) {
            float[] p = zoomCalibPoints.get(0);
            float calibDist = p[0];
            float calibZoom = p[1];
            if (calibDist <= 0 || calibZoom <= 0) return 1.0f;

            float rawZoom = calibZoom * (distance / calibDist);

            // 施加距离分段上限，防止短距离高变倍导致画面抖动
            float maxZoom;
            if (distance < 1.0f) maxZoom = 3.0f;
            else if (distance < 3.0f) maxZoom = 8.0f;
            else if (distance < 10.0f) maxZoom = 16.0f;
            else if (distance < 30.0f) maxZoom = 25.0f;
            else maxZoom = 35.0f;

            return Math.max(1.0f, Math.min(maxZoom, rawZoom));
        }

        List<float[]> sorted = new ArrayList<>(zoomCalibPoints);
        sorted.sort(Comparator.comparingDouble(a -> a[0]));

        // 超出标定范围时夹紧到端点值（不做外推）
        if (distance <= sorted.get(0)[0]) return Math.max(1.0f, sorted.get(0)[1]);
        if (distance >= sorted.get(sorted.size() - 1)[0]) {
            return Math.max(1.0f, Math.min(35.0f, sorted.get(sorted.size() - 1)[1]));
        }

        for (int i = 0; i < sorted.size() - 1; i++) {
            if (distance >= sorted.get(i)[0] && distance <= sorted.get(i + 1)[0]) {
                float t = (distance - sorted.get(i)[0]) / (sorted.get(i + 1)[0] - sorted.get(i)[0]);
                float zoom = sorted.get(i)[1] + t * (sorted.get(i + 1)[1] - sorted.get(i)[1]);
                return Math.max(1.0f, Math.min(35.0f, zoom));
            }
        }
        return Math.max(1.0f, zoomCalibPoints.get(0)[1]);
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
