package com.digital.video.gateway.driver.livox.algorithm;

import com.digital.video.gateway.driver.livox.model.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 雷达-球机坐标系标定求解器。
 * 根据标定点对（雷达坐标 + 球机实际瞄准角度）求解坐标变换参数。
 *
 * 物理模型：P_camera = Rx(θx) · Rz(θz) · P_radar + T
 * PTZ 角度：pan = atan2(P_camera.y, P_camera.x), tilt = atan2(P_camera.z, horiz)
 *
 * 求解策略（所有模式 translation 固定为 0，仅求解旋转偏移）：
 * - 1 点：解析解求 rotationZ，二阶段搜索求 rotationX
 * - 2+ 点：Gauss-Newton 最小二乘优化 rotationZ、rotationX
 */
public class CalibrationSolver {

    private static final Logger logger = LoggerFactory.getLogger(CalibrationSolver.class);

    private static final float SCALE_FIXED = 1.0f;
    private static final float ROTATION_Y_FIXED = 0f;
    private static final int MAX_ITERATIONS = 50;
    private static final float CONVERGE_THRESHOLD_DEG = 0.001f;
    private static final float STEP_DAMP = 0.5f;

    public static class CalibrationPoint {
        public final float radarX;
        public final float radarY;
        public final float radarZ;
        public final float cameraPan;  // 度，0-360
        public final float cameraTilt; // 度，约 -90~90

        public CalibrationPoint(float radarX, float radarY, float radarZ, float cameraPan, float cameraTilt) {
            this.radarX = radarX;
            this.radarY = radarY;
            this.radarZ = radarZ;
            this.cameraPan = cameraPan;
            this.cameraTilt = cameraTilt;
        }
    }

    public static class CalibrationResult {
        public final float translationX;
        public final float translationY;
        public final float translationZ;
        public final float rotationX;
        public final float rotationY;
        public final float rotationZ;
        public final float scale;
        public final float avgErrorDegrees;
        public final float maxErrorDegrees;
        public final float[] perPointPanErrorDegrees;
        public final float[] perPointTiltErrorDegrees;

        public CalibrationResult(float tx, float ty, float tz,
                                 float rx, float ry, float rz, float scale,
                                 float avgErrorDegrees, float maxErrorDegrees,
                                 float[] perPointPanErrorDegrees, float[] perPointTiltErrorDegrees) {
            this.translationX = tx;
            this.translationY = ty;
            this.translationZ = tz;
            this.rotationX = rx;
            this.rotationY = ry;
            this.rotationZ = rz;
            this.scale = scale;
            this.avgErrorDegrees = avgErrorDegrees;
            this.maxErrorDegrees = maxErrorDegrees;
            this.perPointPanErrorDegrees = perPointPanErrorDegrees;
            this.perPointTiltErrorDegrees = perPointTiltErrorDegrees;
        }
    }

    public static CalibrationResult solve(List<CalibrationPoint> points) {
        if (points == null || points.isEmpty()) {
            return null;
        }

        for (int i = 0; i < points.size(); i++) {
            CalibrationPoint p = points.get(i);
            logger.info("标定输入点[{}]: radar=({}, {}, {}), camera pan={}°, tilt={}°",
                    i, p.radarX, p.radarY, p.radarZ, p.cameraPan, p.cameraTilt);
        }

        CalibrationResult result;
        if (points.size() == 1) {
            result = solveSinglePoint(points.get(0));
        } else {
            result = solveMultiPoint(points);
        }

        if (result != null) {
            logger.info("标定结果: rotZ={}°, rotX={}°, tx={}, ty={}, tz={}, avgErr={}°, maxErr={}°",
                    result.rotationZ, result.rotationX,
                    result.translationX, result.translationY, result.translationZ,
                    result.avgErrorDegrees, result.maxErrorDegrees);

            if (!isResultReasonable(result)) {
                logger.warn("标定结果超出合理范围，已拒绝: rotZ={}°, rotX={}°, tx={}, ty={}, tz={}",
                        result.rotationZ, result.rotationX,
                        result.translationX, result.translationY, result.translationZ);
                return null;
            }
        }

        return result;
    }

    private static boolean isResultReasonable(CalibrationResult r) {
        if (Math.abs(r.rotationX) > 180 || Math.abs(r.rotationZ) > 180) return false;
        if (Math.abs(r.translationX) > 50 || Math.abs(r.translationY) > 50 || Math.abs(r.translationZ) > 50)
            return false;
        if (r.avgErrorDegrees > 90) return false;
        return true;
    }

    /**
     * 单点标定：解析解求 rotationZ，二阶段搜索求 rotationX，translation=0
     */
    private static CalibrationResult solveSinglePoint(CalibrationPoint p) {
        double panRad = Math.toRadians(normalizePan(p.cameraPan));
        float rx = p.radarX;
        float ry = p.radarY;
        float rz = p.radarZ;

        double sinPan = Math.sin(panRad);
        double cosPan = Math.cos(panRad);
        double denom = rx * cosPan + ry * sinPan;
        double num = rx * sinPan - ry * cosPan;
        float rotationZ = (float) Math.toDegrees(Math.atan2(num, denom));

        double rzRad = Math.toRadians(rotationZ);
        double cosZ = Math.cos(rzRad);
        double sinZ = Math.sin(rzRad);
        double x1 = rx * cosZ - ry * sinZ;
        double y1 = rx * sinZ + ry * cosZ;
        double z1 = rz;

        float rotationX = solveRotationXForTilt((float) x1, (float) y1, (float) z1, p.cameraTilt);

        float tx = 0, ty = 0, tz = 0;

        CoordinateTransform t = new CoordinateTransform(tx, ty, tz, rotationX, ROTATION_Y_FIXED, rotationZ, SCALE_FIXED);
        Point cam = t.transformRadarToCamera(new Point(rx, ry, rz));
        float[] angles = t.calculatePTZAngles(cam);
        float panErr = normalizeAngleDeg(angles[0] - normalizePan(p.cameraPan));
        float tiltErr = angles[1] - p.cameraTilt;
        float maxErr = Math.max(Math.abs(panErr), Math.abs(tiltErr));

        return new CalibrationResult(tx, ty, tz, rotationX, ROTATION_Y_FIXED, rotationZ, SCALE_FIXED,
                maxErr, maxErr,
                new float[]{panErr}, new float[]{tiltErr});
    }

    /**
     * 二阶段搜索 rotationX：粗搜 1° 步长 → 细搜 0.05° 步长
     */
    private static float solveRotationXForTilt(float x1, float y1, float z1, float tiltTargetDeg) {
        float bestX = 0;
        float bestErr = Float.MAX_VALUE;
        for (float xDeg = -60f; xDeg <= 60f; xDeg += 1.0f) {
            float err = Math.abs(computeTilt(x1, y1, z1, xDeg) - tiltTargetDeg);
            if (err < bestErr) {
                bestErr = err;
                bestX = xDeg;
            }
        }
        float coarseCenter = bestX;
        for (float xDeg = coarseCenter - 1.5f; xDeg <= coarseCenter + 1.5f; xDeg += 0.05f) {
            float err = Math.abs(computeTilt(x1, y1, z1, xDeg) - tiltTargetDeg);
            if (err < bestErr) {
                bestErr = err;
                bestX = xDeg;
            }
        }
        return bestX;
    }

    private static float computeTilt(float x1, float y1, float z1, float rotXDeg) {
        double xRad = Math.toRadians(rotXDeg);
        double cosX = Math.cos(xRad);
        double sinX = Math.sin(xRad);
        double cx = x1;
        double cy = y1 * cosX - z1 * sinX;
        double cz = y1 * sinX + z1 * cosX;
        double horiz = Math.sqrt(cx * cx + cy * cy);
        return (float) Math.toDegrees(Math.atan2(cz, horiz));
    }

    /**
     * 多点标定：Gauss-Newton 最小二乘，仅优化 [rotZ, rotX]（2 个未知数），translation 固定为 0。
     * 这样即使只有 2 个标定点（4 个方程），系统也是过定的，优化稳定。
     */
    private static CalibrationResult solveMultiPoint(List<CalibrationPoint> points) {
        CalibrationResult first = solveSinglePoint(points.get(0));
        double rotZ = Math.toRadians(first.rotationZ);
        double rotX = Math.toRadians(first.rotationX);

        int n = points.size();
        int numParams = 2; // rotZ, rotX

        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            double[] residual = new double[n * 2];
            double[][] jacobian = new double[n * 2][numParams];
            double delta = 1e-7;

            for (int i = 0; i < n; i++) {
                CalibrationPoint p = points.get(i);
                double[] angles = forward(p.radarX, p.radarY, p.radarZ, rotZ, rotX, 0, 0, 0);
                float panObs = normalizePan(p.cameraPan);
                float tiltObs = p.cameraTilt;
                residual[i * 2] = normalizeAngleDeg((float) (angles[0] - panObs));
                residual[i * 2 + 1] = angles[1] - tiltObs;

                // d/d(rotZ)
                double[] anglesPlus = forward(p.radarX, p.radarY, p.radarZ, rotZ + delta, rotX, 0, 0, 0);
                jacobian[i * 2][0] = normalizeAngleDeg((float) (anglesPlus[0] - angles[0])) / delta;
                jacobian[i * 2 + 1][0] = (anglesPlus[1] - angles[1]) / delta;

                // d/d(rotX)
                anglesPlus = forward(p.radarX, p.radarY, p.radarZ, rotZ, rotX + delta, 0, 0, 0);
                jacobian[i * 2][1] = normalizeAngleDeg((float) (anglesPlus[0] - angles[0])) / delta;
                jacobian[i * 2 + 1][1] = (anglesPlus[1] - angles[1]) / delta;
            }

            // J^T J dx = -J^T r （2x2 系统）
            double[][] JtJ = new double[numParams][numParams];
            double[] Jtr = new double[numParams];
            for (int i = 0; i < numParams; i++) {
                for (int k = 0; k < numParams; k++) {
                    double sum = 0;
                    for (int r = 0; r < n * 2; r++) sum += jacobian[r][i] * jacobian[r][k];
                    JtJ[i][k] = sum;
                }
                double sum = 0;
                for (int r = 0; r < n * 2; r++) sum += jacobian[r][i] * residual[r];
                Jtr[i] = -sum;
            }

            // 求解 2x2 线性系统
            double det = JtJ[0][0] * JtJ[1][1] - JtJ[0][1] * JtJ[1][0];
            if (Math.abs(det) < 1e-20) {
                logger.warn("标定 GN 迭代: JtJ 矩阵奇异, iter={}", iter);
                break;
            }
            double dRotZ = (JtJ[1][1] * Jtr[0] - JtJ[0][1] * Jtr[1]) / det;
            double dRotX = (JtJ[0][0] * Jtr[1] - JtJ[1][0] * Jtr[0]) / det;

            rotZ += STEP_DAMP * dRotZ;
            rotX += STEP_DAMP * dRotX;

            double maxDx = Math.max(Math.abs(dRotZ), Math.abs(dRotX));
            if (maxDx < Math.toRadians(CONVERGE_THRESHOLD_DEG)) {
                logger.info("标定 GN 收敛于第 {} 次迭代, maxDx={}rad", iter, maxDx);
                break;
            }
        }

        float rotationZ = normalizeAngleDeg((float) Math.toDegrees(rotZ));
        float rotationX = normalizeAngleDeg((float) Math.toDegrees(rotX));
        float tx = 0, ty = 0, tz = 0;

        float[] perPan = new float[n];
        float[] perTilt = new float[n];
        float sumErr = 0;
        float maxErr = 0;
        CoordinateTransform t = new CoordinateTransform(tx, ty, tz, rotationX, ROTATION_Y_FIXED, rotationZ, SCALE_FIXED);
        for (int i = 0; i < n; i++) {
            CalibrationPoint p = points.get(i);
            Point cam = t.transformRadarToCamera(new Point(p.radarX, p.radarY, p.radarZ));
            float[] angles = t.calculatePTZAngles(cam);
            float panErr = normalizeAngleDeg(angles[0] - normalizePan(p.cameraPan));
            float tiltErr = angles[1] - p.cameraTilt;
            perPan[i] = panErr;
            perTilt[i] = tiltErr;
            float e = (float) Math.sqrt(panErr * panErr + tiltErr * tiltErr);
            sumErr += e;
            if (e > maxErr) maxErr = e;
            logger.info("标定点[{}]误差: panErr={}°, tiltErr={}°, 总={}°", i, panErr, tiltErr, e);
        }
        float avgErr = sumErr / n;

        return new CalibrationResult(tx, ty, tz, rotationX, ROTATION_Y_FIXED, rotationZ, SCALE_FIXED,
                avgErr, maxErr, perPan, perTilt);
    }

    private static double[] forward(float rx, float ry, float rz, double rotZ, double rotX, double tx, double ty, double tz) {
        double cosZ = Math.cos(rotZ);
        double sinZ = Math.sin(rotZ);
        double x1 = rx * cosZ - ry * sinZ;
        double y1 = rx * sinZ + ry * cosZ;
        double z1 = rz;
        double cosX = Math.cos(rotX);
        double sinX = Math.sin(rotX);
        double cx = x1 + tx;
        double cy = y1 * cosX - z1 * sinX + ty;
        double cz = y1 * sinX + z1 * cosX + tz;
        double pan = Math.toDegrees(Math.atan2(cy, cx));
        double horiz = Math.sqrt(cx * cx + cy * cy);
        double tilt = Math.toDegrees(Math.atan2(cz, horiz));
        pan = (pan + 360) % 360;
        return new double[]{pan, tilt};
    }

    private static float normalizePan(float pan) {
        float p = pan % 360f;
        if (p < 0) p += 360f;
        return p;
    }

    private static float normalizeAngleDeg(float deg) {
        while (deg > 180f) deg -= 360f;
        while (deg < -180f) deg += 360f;
        return deg;
    }
}
