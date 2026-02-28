package com.digital.video.gateway.driver.livox.algorithm;

/**
 * 简易卡尔曼滤波器（6维状态：位置 + 速度）。
 * 状态向量: [x, y, z, vx, vy, vz]
 * 观测向量: [x, y, z]
 * 用于目标跟踪中的位置预测与修正。
 */
public class SimpleKalmanFilter {

    private final double[] state;       // [x, y, z, vx, vy, vz]
    private final double[][] P;         // 6x6 协方差矩阵
    private long lastTimestampMs;

    private static final double PROCESS_NOISE = 0.5;
    private static final double MEASUREMENT_NOISE = 0.3;

    public SimpleKalmanFilter(double x, double y, double z, long timestampMs) {
        this.state = new double[]{x, y, z, 0, 0, 0};
        this.P = identityScaled(6, 1.0);
        this.lastTimestampMs = timestampMs;
    }

    /**
     * 预测步骤：根据上一次状态和时间差推算当前位置。
     * 返回预测后的位置 [x, y, z]。
     */
    public double[] predict(long currentTimestampMs) {
        double dt = (currentTimestampMs - lastTimestampMs) / 1000.0;
        if (dt <= 0) dt = 0.1;
        if (dt > 10.0) dt = 10.0;

        state[0] += state[3] * dt;
        state[1] += state[4] * dt;
        state[2] += state[5] * dt;

        double dt2 = dt * dt;
        for (int i = 0; i < 6; i++) {
            P[i][i] += PROCESS_NOISE * (i < 3 ? dt2 : dt);
        }
        for (int i = 0; i < 3; i++) {
            P[i][i + 3] += PROCESS_NOISE * dt;
            P[i + 3][i] += PROCESS_NOISE * dt;
        }

        sanitizeState();
        this.lastTimestampMs = currentTimestampMs;
        return new double[]{state[0], state[1], state[2]};
    }

    /**
     * 更新步骤：用观测值修正预测。
     */
    public void update(double mx, double my, double mz, long timestampMs) {
        if (Double.isNaN(mx) || Double.isNaN(my) || Double.isNaN(mz)
                || Double.isInfinite(mx) || Double.isInfinite(my) || Double.isInfinite(mz)) {
            return;
        }
        double dt = (timestampMs - lastTimestampMs) / 1000.0;
        if (dt <= 0) dt = 0.1;
        if (dt > 10.0) dt = 10.0;

        double[] innovation = {
                mx - state[0],
                my - state[1],
                mz - state[2]
        };

        double[][] S = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                S[i][j] = P[i][j];
            }
            S[i][i] += MEASUREMENT_NOISE;
        }

        double[][] SInv = invert3x3(S);

        // K = P[:, 0:3] * S^-1  (6x3 * 3x3 = 6x3)
        double[][] K = new double[6][3];
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 3; j++) {
                for (int k = 0; k < 3; k++) {
                    K[i][j] += P[i][k] * SInv[k][j];
                }
            }
        }

        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 3; j++) {
                state[i] += K[i][j] * innovation[j];
            }
        }

        double[][] newP = new double[6][6];
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 6; j++) {
                newP[i][j] = P[i][j];
                for (int k = 0; k < 3; k++) {
                    newP[i][j] -= K[i][k] * P[k][j];
                }
            }
        }
        for (int i = 0; i < 6; i++) {
            System.arraycopy(newP[i], 0, P[i], 0, 6);
        }

        // 从观测反推速度
        state[3] = innovation[0] / dt;
        state[4] = innovation[1] / dt;
        state[5] = innovation[2] / dt;

        sanitizeState();
        this.lastTimestampMs = timestampMs;
    }

    private static final double MAX_POSITION = 500.0;
    private static final double MAX_VELOCITY = 50.0;
    private static final double MAX_COVARIANCE = 1e6;

    private void sanitizeState() {
        boolean reset = false;
        for (int i = 0; i < 6; i++) {
            if (Double.isNaN(state[i]) || Double.isInfinite(state[i])) {
                reset = true;
                break;
            }
        }
        if (!reset) {
            for (int i = 0; i < 3; i++) {
                if (Math.abs(state[i]) > MAX_POSITION || Math.abs(state[i + 3]) > MAX_VELOCITY) {
                    reset = true;
                    break;
                }
            }
        }
        if (reset) {
            for (int i = 3; i < 6; i++) state[i] = 0;
            double[][] fresh = identityScaled(6, 1.0);
            for (int i = 0; i < 6; i++) System.arraycopy(fresh[i], 0, P[i], 0, 6);
            return;
        }
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 6; j++) {
                if (Double.isNaN(P[i][j]) || Double.isInfinite(P[i][j]) || Math.abs(P[i][j]) > MAX_COVARIANCE) {
                    P[i][j] = (i == j) ? 1.0 : 0.0;
                }
            }
        }
    }

    public double[] getPosition() {
        return new double[]{state[0], state[1], state[2]};
    }

    public double[] getVelocity() {
        return new double[]{state[3], state[4], state[5]};
    }

    public long getLastTimestampMs() {
        return lastTimestampMs;
    }

    // ---- 工具方法 ----

    private static double[][] identityScaled(int n, double scale) {
        double[][] m = new double[n][n];
        for (int i = 0; i < n; i++) m[i][i] = scale;
        return m;
    }

    private static double[][] invert3x3(double[][] m) {
        double det = m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1])
                   - m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0])
                   + m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0]);
        if (Math.abs(det) < 1e-10) {
            return identityScaled(3, 1.0 / MEASUREMENT_NOISE);
        }
        double invDet = 1.0 / det;
        double[][] inv = new double[3][3];
        inv[0][0] = (m[1][1] * m[2][2] - m[1][2] * m[2][1]) * invDet;
        inv[0][1] = (m[0][2] * m[2][1] - m[0][1] * m[2][2]) * invDet;
        inv[0][2] = (m[0][1] * m[1][2] - m[0][2] * m[1][1]) * invDet;
        inv[1][0] = (m[1][2] * m[2][0] - m[1][0] * m[2][2]) * invDet;
        inv[1][1] = (m[0][0] * m[2][2] - m[0][2] * m[2][0]) * invDet;
        inv[1][2] = (m[0][2] * m[1][0] - m[0][0] * m[1][2]) * invDet;
        inv[2][0] = (m[1][0] * m[2][1] - m[1][1] * m[2][0]) * invDet;
        inv[2][1] = (m[0][1] * m[2][0] - m[0][0] * m[2][1]) * invDet;
        inv[2][2] = (m[0][0] * m[1][1] - m[0][1] * m[1][0]) * invDet;
        return inv;
    }
}
