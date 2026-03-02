package com.digital.video.gateway.driver.livox.algorithm;

import com.digital.video.gateway.driver.livox.model.Point;

import java.util.List;

/**
 * 雷达-球机坐标系标定求解器。
 * 根据标定点对（雷达坐标 + 球机实际瞄准角度）求解坐标变换参数。
 * - 1 点：快速模式，仅求解 rotationZ、rotationX，其余为零
 * - 2+ 点：精确模式，Gauss-Newton 迭代求解 rotationZ、rotationX、translation
 */
public class CalibrationSolver {

    private static final float SCALE_FIXED = 1.0f;
    private static final float ROTATION_Y_FIXED = 0f;
    private static final int MAX_ITERATIONS = 30;
    private static final float CONVERGE_THRESHOLD_DEG = 0.01f;
    private static final float STEP_DAMP = 0.3f;

    /**
     * 标定点：雷达坐标 + 球机瞄准角度（度）
     */
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

    /**
     * 标定结果：变换参数 + 误差
     */
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

    /**
     * 求解标定参数
     *
     * @param points 标定点列表，至少 1 个；推荐 2-3 个
     * @return 标定结果，若 points 为空返回 null
     */
    public static CalibrationResult solve(List<CalibrationPoint> points) {
        if (points == null || points.isEmpty()) {
            return null;
        }
        if (points.size() == 1) {
            return solveSinglePoint(points.get(0));
        }
        return solveMultiPoint(points);
    }

    /**
     * 单点标定：仅求解 rotationZ、rotationX，translation=0, scale=1, rotationY=0
     */
    private static CalibrationResult solveSinglePoint(CalibrationPoint p) {
        double panRad = Math.toRadians(normalizePan(p.cameraPan));
        double tiltRad = Math.toRadians(p.cameraTilt);
        float rx = p.radarX;
        float ry = p.radarY;
        float rz = p.radarZ;

        // rotationZ: 使水平方向与 pan 一致
        // 需要 atan2(rx*sinZ+ry*cosZ, rx*cosZ-ry*sinZ) = panRad
        // 推导得 tanZ = (rx*sinPan - ry*cosPan) / (rx*cosPan + ry*sinPan)
        double sinPan = Math.sin(panRad);
        double cosPan = Math.cos(panRad);
        double denom = rx * cosPan + ry * sinPan;
        double num = rx * sinPan - ry * cosPan;
        float rotationZ = (float) Math.toDegrees(Math.atan2(num, denom));

        // 应用 rotationZ 后的点 (rotationY=0, 仅 Z)
        double rzRad = Math.toRadians(rotationZ);
        double cosZ = Math.cos(rzRad);
        double sinZ = Math.sin(rzRad);
        double x1 = rx * cosZ - ry * sinZ;
        double y1 = rx * sinZ + ry * cosZ;
        double z1 = rz;

        // rotationX: 使俯仰与 tilt 一致，用一维搜索
        float rotationX = solveRotationXForTilt((float) x1, (float) y1, (float) z1, (float) Math.toDegrees(tiltRad));

        float tx = 0, ty = 0, tz = 0;
        float scale = SCALE_FIXED;
        float rotationY = ROTATION_Y_FIXED;

        // 计算该单点的误差
        CoordinateTransform t = new CoordinateTransform(tx, ty, tz, rotationX, rotationY, rotationZ, scale);
        Point cam = t.transformRadarToCamera(new Point(rx, ry, rz));
        float[] angles = t.calculatePTZAngles(cam);
        float panErr = normalizeAngleDeg(angles[0] - normalizePan(p.cameraPan));
        float tiltErr = angles[1] - p.cameraTilt;
        float maxErr = Math.max(Math.abs(panErr), Math.abs(tiltErr));

        return new CalibrationResult(tx, ty, tz, rotationX, rotationY, rotationZ, scale,
                maxErr, maxErr,
                new float[]{panErr}, new float[]{tiltErr});
    }

    private static float solveRotationXForTilt(float x1, float y1, float z1, float tiltTargetDeg) {
        float bestX = 0;
        float bestErr = Float.MAX_VALUE;
        for (float xDeg = -45f; xDeg <= 45f; xDeg += 0.5f) {
            double xRad = Math.toRadians(xDeg);
            double cosX = Math.cos(xRad);
            double sinX = Math.sin(xRad);
            double cx = x1;
            double cy = y1 * cosX - z1 * sinX;
            double cz = y1 * sinX + z1 * cosX;
            double horiz = Math.sqrt(cx * cx + cy * cy);
            float tiltDeg = (float) Math.toDegrees(Math.atan2(cz, horiz));
            float err = Math.abs(tiltDeg - tiltTargetDeg);
            if (err < bestErr) {
                bestErr = err;
                bestX = xDeg;
            }
        }
        return bestX;
    }

    /**
     * 多点标定：Gauss-Newton 迭代，状态 [rotationZ, rotationX, translationX, translationY, translationZ]
     */
    private static CalibrationResult solveMultiPoint(List<CalibrationPoint> points) {
        // 初值：用第一个点单点解
        CalibrationResult first = solveSinglePoint(points.get(0));
        double rotZ = Math.toRadians(first.rotationZ);
        double rotX = Math.toRadians(first.rotationX);
        double tx = first.translationX;
        double ty = first.translationY;
        double tz = first.translationZ;

        int n = points.size();
        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            double[] residual = new double[n * 2]; // [panErr0, tiltErr0, panErr1, tiltErr1, ...]
            double[][] jacobian = new double[n * 2][5]; // d(pan,tilt)/d(rotZ, rotX, tx, ty, tz)
            double delta = 1e-6;

            for (int i = 0; i < n; i++) {
                CalibrationPoint p = points.get(i);
                double[] angles = forward(p.radarX, p.radarY, p.radarZ, rotZ, rotX, tx, ty, tz);
                float panObs = normalizePan(p.cameraPan);
                float tiltObs = p.cameraTilt;
                residual[i * 2] = normalizeAngleDeg((float) (angles[0] - panObs));
                residual[i * 2 + 1] = angles[1] - tiltObs;

                for (int j = 0; j < 5; j++) {
                    double[] anglesPlus = forward(p.radarX, p.radarY, p.radarZ,
                            j == 0 ? rotZ + delta : rotZ,
                            j == 1 ? rotX + delta : rotX,
                            j == 2 ? tx + delta : tx,
                            j == 3 ? ty + delta : ty,
                            j == 4 ? tz + delta : tz);
                    jacobian[i * 2][j] = normalizeAngleDeg((float) (anglesPlus[0] - angles[0])) / delta;
                    jacobian[i * 2 + 1][j] = (float) ((anglesPlus[1] - angles[1]) / delta);
                }
            }

            // J^T J dx = -J^T r
            double[][] JtJ = new double[5][5];
            double[] Jtr = new double[5];
            for (int i = 0; i < 5; i++) {
                for (int k = 0; k < 5; k++) {
                    double sum = 0;
                    for (int r = 0; r < n * 2; r++) sum += jacobian[r][i] * jacobian[r][k];
                    JtJ[i][k] = sum;
                }
                double sum = 0;
                for (int r = 0; r < n * 2; r++) sum += jacobian[r][i] * residual[r];
                Jtr[i] = -sum;
            }

            double[] dx = solve5(JtJ, Jtr);
            if (dx == null) break;
            rotZ += STEP_DAMP * dx[0];
            rotX += STEP_DAMP * dx[1];
            tx += STEP_DAMP * dx[2];
            ty += STEP_DAMP * dx[3];
            tz += STEP_DAMP * dx[4];

            double maxDx = 0;
            for (double d : dx) if (Math.abs(d) > maxDx) maxDx = Math.abs(d);
            if (maxDx < Math.toRadians(CONVERGE_THRESHOLD_DEG)) break;
        }

        float rotationZ = (float) Math.toDegrees(rotZ);
        float rotationX = (float) Math.toDegrees(rotX);
        float translationX = (float) tx;
        float translationY = (float) ty;
        float translationZ = (float) tz;

        float[] perPan = new float[n];
        float[] perTilt = new float[n];
        float sumErr = 0;
        float maxErr = 0;
        CoordinateTransform t = new CoordinateTransform(translationX, translationY, translationZ,
                rotationX, ROTATION_Y_FIXED, rotationZ, SCALE_FIXED);
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
        }
        float avgErr = sumErr / n;

        return new CalibrationResult(translationX, translationY, translationZ,
                rotationX, ROTATION_Y_FIXED, rotationZ, SCALE_FIXED,
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

    private static double[] solve5(double[][] A, double[] b) {
        double[][] M = new double[5][6];
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) M[i][j] = A[i][j];
            M[i][5] = b[i];
        }
        for (int col = 0; col < 5; col++) {
            int maxRow = col;
            for (int row = col + 1; row < 5; row++) {
                if (Math.abs(M[row][col]) > Math.abs(M[maxRow][col])) maxRow = row;
            }
            double[] tmp = M[col];
            M[col] = M[maxRow];
            M[maxRow] = tmp;
            double pivot = M[col][col];
            if (Math.abs(pivot) < 1e-12) return null;
            for (int j = 0; j < 6; j++) M[col][j] /= pivot;
            for (int i = 0; i < 5; i++) {
                if (i == col) continue;
                double f = M[i][col];
                for (int j = 0; j < 6; j++) M[i][j] -= f * M[col][j];
            }
        }
        double[] x = new double[5];
        for (int i = 0; i < 5; i++) x[i] = M[i][5];
        return x;
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
