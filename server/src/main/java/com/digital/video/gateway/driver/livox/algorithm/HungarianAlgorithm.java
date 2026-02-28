package com.digital.video.gateway.driver.livox.algorithm;

import java.util.Arrays;

/**
 * 匈牙利算法（Kuhn-Munkres）。
 * 用于将已有轨迹的预测位置与本帧检测到的聚类进行最优匹配（最小代价）。
 *
 * 输入: float[][] costMatrix (rows=轨迹, cols=检测)
 * 输出: int[] assignment (长度=rows, assignment[i]=匹配的列, -1表示未匹配)
 */
public class HungarianAlgorithm {

    private static final float INF = Float.MAX_VALUE / 2;

    /**
     * 求解最小代价匹配。
     *
     * @param costMatrix 代价矩阵，rows=轨迹数, cols=检测数
     * @param maxCost    最大可接受的匹配距离，超过此值视为不可匹配
     * @return int[] 长度为 rows，assignment[i] 表示第 i 行(轨迹)匹配的列(检测)索引，-1 表示未匹配
     */
    public int[] solve(float[][] costMatrix, float maxCost) {
        if (costMatrix == null || costMatrix.length == 0) {
            return new int[0];
        }

        int rows = costMatrix.length;
        int cols = costMatrix[0].length;

        if (cols == 0) {
            int[] result = new int[rows];
            Arrays.fill(result, -1);
            return result;
        }

        int n = Math.max(rows, cols);
        float[][] cost = new float[n][n];
        for (float[] row : cost) Arrays.fill(row, INF);
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                cost[i][j] = costMatrix[i][j];
            }
        }

        float[] u = new float[n + 1];
        float[] v = new float[n + 1];
        int[] p = new int[n + 1];
        int[] way = new int[n + 1];

        for (int i = 1; i <= n; i++) {
            p[0] = i;
            int j0 = 0;
            float[] minv = new float[n + 1];
            boolean[] used = new boolean[n + 1];
            Arrays.fill(minv, INF);

            do {
                used[j0] = true;
                int i0 = p[j0];
                float delta = INF;
                int j1 = -1;

                for (int j = 1; j <= n; j++) {
                    if (!used[j]) {
                        float cur = cost[i0 - 1][j - 1] - u[i0] - v[j];
                        if (cur < minv[j]) {
                            minv[j] = cur;
                            way[j] = j0;
                        }
                        if (minv[j] < delta) {
                            delta = minv[j];
                            j1 = j;
                        }
                    }
                }

                if (j1 < 0) break;

                for (int j = 0; j <= n; j++) {
                    if (used[j]) {
                        u[p[j]] += delta;
                        v[j] -= delta;
                    } else {
                        minv[j] -= delta;
                    }
                }

                j0 = j1;
            } while (j0 > 0 && p[j0] != 0);

            if (j0 > 0) {
                do {
                    int j1 = way[j0];
                    p[j0] = p[j1];
                    j0 = j1;
                } while (j0 != 0);
            }
        }

        int[] result = new int[rows];
        Arrays.fill(result, -1);

        for (int j = 1; j <= n; j++) {
            if (p[j] != 0 && p[j] <= rows && j <= cols) {
                if (costMatrix[p[j] - 1][j - 1] <= maxCost) {
                    result[p[j] - 1] = j - 1;
                }
            }
        }

        return result;
    }
}
