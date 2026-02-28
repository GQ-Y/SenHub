package com.digital.video.gateway.service;

import com.digital.video.gateway.driver.livox.algorithm.HungarianAlgorithm;
import com.digital.video.gateway.driver.livox.algorithm.SimpleKalmanFilter;
import com.digital.video.gateway.driver.livox.model.Point;
import com.digital.video.gateway.driver.livox.model.PointCluster;
import com.digital.video.gateway.driver.livox.model.TargetType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 目标跟踪服务
 * 用于跟踪目标的运动轨迹，支持多目标同时跟踪
 */
public class TargetTrackingService {
    private static final Logger logger = LoggerFactory.getLogger(TargetTrackingService.class);

    /**
     * 目标轨迹点
     */
    public static class TrajectoryPoint {
        public Point position;      // 位置
        public long timestamp;      // 时间戳（毫秒）
        public float velocityX;     // X方向速度（米/秒）
        public float velocityY;     // Y方向速度（米/秒）
        public float velocityZ;     // Z方向速度（米/秒）

        public TrajectoryPoint(Point position, long timestamp) {
            this.position = position;
            this.timestamp = timestamp;
        }

        public TrajectoryPoint(Point position, long timestamp, float vx, float vy, float vz) {
            this.position = position;
            this.timestamp = timestamp;
            this.velocityX = vx;
            this.velocityY = vy;
            this.velocityZ = vz;
        }
    }

    /**
     * 目标轨迹
     */
    public static class TargetTrajectory {
        private String targetId;                           // 目标ID（通常使用clusterId）
        private List<TrajectoryPoint> points;              // 轨迹点列表
        private long lastUpdateTime;                       // 最后更新时间
        private static final long MAX_AGE_MS = 5000;       // 轨迹最大保留时间（5秒）
        private static final int MAX_POINTS = 50;          // 最大轨迹点数

        public TargetTrajectory(String targetId) {
            this.targetId = targetId;
            this.points = new ArrayList<>();
            this.lastUpdateTime = System.currentTimeMillis();
        }

        /**
         * 添加新的轨迹点
         */
        public void addPoint(Point position, long timestamp) {
            // 计算速度（如果有上一个点）
            float vx = 0, vy = 0, vz = 0;
            if (!points.isEmpty()) {
                TrajectoryPoint lastPoint = points.get(points.size() - 1);
                long dt = timestamp - lastPoint.timestamp;
                if (dt > 0) {
                    float dtSeconds = dt / 1000.0f;
                    vx = (position.x - lastPoint.position.x) / dtSeconds;
                    vy = (position.y - lastPoint.position.y) / dtSeconds;
                    vz = (position.z - lastPoint.position.z) / dtSeconds;
                }
            }

            TrajectoryPoint newPoint = new TrajectoryPoint(position, timestamp, vx, vy, vz);
            points.add(newPoint);
            lastUpdateTime = timestamp;

            // 限制轨迹点数量
            if (points.size() > MAX_POINTS) {
                points.remove(0);
            }

            // 清理过期点
            long currentTime = System.currentTimeMillis();
            points.removeIf(p -> currentTime - p.timestamp > MAX_AGE_MS);
        }

        /**
         * 获取最新的位置
         */
        public Point getLatestPosition() {
            return points.isEmpty() ? null : points.get(points.size() - 1).position;
        }

        /**
         * 获取最新的速度
         */
        public float[] getLatestVelocity() {
            if (points.isEmpty()) {
                return new float[]{0, 0, 0};
            }
            TrajectoryPoint latest = points.get(points.size() - 1);
            return new float[]{latest.velocityX, latest.velocityY, latest.velocityZ};
        }

        /**
         * 获取所有轨迹点
         */
        public List<TrajectoryPoint> getPoints() {
            return new ArrayList<>(points);
        }

        /**
         * 检查轨迹是否过期
         */
        public boolean isExpired() {
            return System.currentTimeMillis() - lastUpdateTime > MAX_AGE_MS;
        }

        public String getTargetId() {
            return targetId;
        }

        public long getLastUpdateTime() {
            return lastUpdateTime;
        }
    }

    /**
     * 跨帧跟踪目标：维护稳定ID、卡尔曼滤波器、分类类型
     */
    public static class TrackedTarget {
        private final String trackingId;
        private SimpleKalmanFilter kalman;
        private TargetType targetType;
        private PointCluster latestCluster;
        private long lastUpdateTime;
        private int missCount;

        public TrackedTarget(String trackingId, PointCluster cluster, long timestampMs) {
            this.trackingId = trackingId;
            Point c = cluster.getCentroid();
            this.kalman = new SimpleKalmanFilter(c.x, c.y, c.z, timestampMs);
            this.targetType = cluster.getTargetType();
            this.latestCluster = cluster;
            this.lastUpdateTime = timestampMs;
            this.missCount = 0;
        }

        public String getTrackingId() { return trackingId; }
        public TargetType getTargetType() { return targetType; }
        public PointCluster getLatestCluster() { return latestCluster; }
        public long getLastUpdateTime() { return lastUpdateTime; }
        public int getMissCount() { return missCount; }
        public SimpleKalmanFilter getKalman() { return kalman; }

        public double[] getPredictedPosition(long timestampMs) {
            return kalman.predict(timestampMs);
        }

        public void update(PointCluster cluster, long timestampMs) {
            Point c = cluster.getCentroid();
            kalman.update(c.x, c.y, c.z, timestampMs);
            if (cluster.getTargetType() != null) {
                this.targetType = cluster.getTargetType();
            }
            this.latestCluster = cluster;
            this.lastUpdateTime = timestampMs;
            this.missCount = 0;
        }

        public void incrementMiss() {
            this.missCount++;
        }

        public Point getPosition() {
            double[] pos = kalman.getPosition();
            return new Point((float) pos[0], (float) pos[1], (float) pos[2]);
        }

        public float[] getVelocity() {
            double[] v = kalman.getVelocity();
            return new float[]{(float) v[0], (float) v[1], (float) v[2]};
        }
    }

    // 目标轨迹存储：targetId -> TargetTrajectory（旧接口兼容）
    private final Map<String, TargetTrajectory> trajectories = new ConcurrentHashMap<>();

    // 跨帧跟踪目标池：trackingId -> TrackedTarget
    private final Map<String, TrackedTarget> trackedTargets = new ConcurrentHashMap<>();
    private final AtomicLong nextTrackingIdSeq = new AtomicLong(1);
    private final HungarianAlgorithm hungarian = new HungarianAlgorithm();

    private static final float ASSOCIATION_MAX_DISTANCE = 2.0f;
    private static final int MAX_MISS_COUNT = 5;

    private Timer cleanupTimer;
    private static final long CLEANUP_INTERVAL_MS = 2000;

    public TargetTrackingService() {
        startCleanupTimer();
    }

    /**
     * 核心方法：将本帧聚类与已有轨迹进行关联和更新。
     * 使用卡尔曼预测 + 匈牙利匹配实现跨帧稳定ID。
     *
     * @param clusters 本帧检测到的聚类列表（已分类）
     * @return 更新后的存活 TrackedTarget 列表
     */
    public List<TrackedTarget> associateAndUpdate(List<PointCluster> clusters) {
        long now = System.currentTimeMillis();

        List<TrackedTarget> existingTracks = new ArrayList<>(trackedTargets.values());

        if (existingTracks.isEmpty()) {
            for (PointCluster cluster : clusters) {
                String tid = generateTrackingId();
                TrackedTarget target = new TrackedTarget(tid, cluster, now);
                trackedTargets.put(tid, target);
                updateTarget(tid, cluster.getCentroid());
                logger.debug("新建跟踪目标: trackingId={}, type={}, centroid={}",
                        tid, target.getTargetType(), cluster.getCentroid());
            }
            return new ArrayList<>(trackedTargets.values());
        }

        if (clusters.isEmpty()) {
            for (TrackedTarget track : existingTracks) {
                track.incrementMiss();
            }
            removeExpiredTracks();
            return new ArrayList<>(trackedTargets.values());
        }

        // 1. 对所有存活轨迹进行卡尔曼预测
        double[][] predictions = new double[existingTracks.size()][];
        for (int i = 0; i < existingTracks.size(); i++) {
            predictions[i] = existingTracks.get(i).getPredictedPosition(now);
        }

        // 2. 构建代价矩阵（预测位置 vs 聚类质心的距离）
        float[][] costMatrix = new float[existingTracks.size()][clusters.size()];
        for (int i = 0; i < existingTracks.size(); i++) {
            for (int j = 0; j < clusters.size(); j++) {
                Point centroid = clusters.get(j).getCentroid();
                double dx = predictions[i][0] - centroid.x;
                double dy = predictions[i][1] - centroid.y;
                double dz = predictions[i][2] - centroid.z;
                costMatrix[i][j] = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            }
        }

        // 3. 匈牙利匹配
        int[] assignment = hungarian.solve(costMatrix, ASSOCIATION_MAX_DISTANCE);

        Set<Integer> matchedClusters = new HashSet<>();

        // 4. 处理匹配结果
        for (int i = 0; i < assignment.length; i++) {
            TrackedTarget track = existingTracks.get(i);
            if (assignment[i] >= 0) {
                PointCluster matchedCluster = clusters.get(assignment[i]);
                track.update(matchedCluster, now);
                updateTarget(track.getTrackingId(), matchedCluster.getCentroid());
                matchedClusters.add(assignment[i]);
                logger.trace("跟踪目标匹配: trackingId={}, cluster={}",
                        track.getTrackingId(), matchedCluster.getClusterId());
            } else {
                track.incrementMiss();
            }
        }

        // 5. 未匹配的聚类 → 新建轨迹
        for (int j = 0; j < clusters.size(); j++) {
            if (!matchedClusters.contains(j)) {
                PointCluster cluster = clusters.get(j);
                String tid = generateTrackingId();
                TrackedTarget target = new TrackedTarget(tid, cluster, now);
                trackedTargets.put(tid, target);
                updateTarget(tid, cluster.getCentroid());
                logger.debug("新建跟踪目标: trackingId={}, type={}, centroid={}",
                        tid, target.getTargetType(), cluster.getCentroid());
            }
        }

        // 6. 移除丢失超时的轨迹
        removeExpiredTracks();

        return new ArrayList<>(trackedTargets.values());
    }

    /**
     * 获取指定跟踪目标
     */
    public TrackedTarget getTrackedTarget(String trackingId) {
        return trackedTargets.get(trackingId);
    }

    /**
     * 获取所有存活的跟踪目标
     */
    public Collection<TrackedTarget> getAllTrackedTargets() {
        return trackedTargets.values();
    }

    private String generateTrackingId() {
        return "trk_" + nextTrackingIdSeq.getAndIncrement();
    }

    private void removeExpiredTracks() {
        Iterator<Map.Entry<String, TrackedTarget>> it = trackedTargets.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, TrackedTarget> entry = it.next();
            if (entry.getValue().getMissCount() > MAX_MISS_COUNT) {
                logger.debug("移除丢失目标: trackingId={}, missCount={}",
                        entry.getKey(), entry.getValue().getMissCount());
                trajectories.remove(entry.getKey());
                it.remove();
            }
        }
    }

    /**
     * 更新目标位置
     * 
     * @param targetId 目标ID（通常使用clusterId）
     * @param position 当前位置
     */
    public void updateTarget(String targetId, Point position) {
        long timestamp = System.currentTimeMillis();
        
        TargetTrajectory trajectory = trajectories.computeIfAbsent(targetId, 
            k -> new TargetTrajectory(targetId));
        
        trajectory.addPoint(position, timestamp);
    }

    /**
     * 获取目标轨迹
     */
    public TargetTrajectory getTrajectory(String targetId) {
        return trajectories.get(targetId);
    }

    /**
     * 获取目标的最新位置
     */
    public Point getLatestPosition(String targetId) {
        TargetTrajectory trajectory = trajectories.get(targetId);
        return trajectory != null ? trajectory.getLatestPosition() : null;
    }

    /**
     * 获取目标的最新速度
     */
    public float[] getLatestVelocity(String targetId) {
        TargetTrajectory trajectory = trajectories.get(targetId);
        return trajectory != null ? trajectory.getLatestVelocity() : new float[]{0, 0, 0};
    }

    /**
     * 移除目标轨迹
     */
    public void removeTarget(String targetId) {
        trajectories.remove(targetId);
    }

    /**
     * 清理过期轨迹
     */
    private static final long TRACKED_TARGET_EXPIRE_MS = 10_000;

    private void startCleanupTimer() {
        cleanupTimer = new Timer("TargetTrackingCleanup", true);
        cleanupTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    // 清理过期轨迹
                    List<String> expiredTrajectories = new ArrayList<>();
                    for (Map.Entry<String, TargetTrajectory> entry : trajectories.entrySet()) {
                        if (entry.getValue().isExpired()) {
                            expiredTrajectories.add(entry.getKey());
                        }
                    }
                    for (String targetId : expiredTrajectories) {
                        trajectories.remove(targetId);
                        logger.debug("清理过期目标轨迹: targetId={}", targetId);
                    }

                    // 清理长时间未更新的跟踪目标（防止 associateAndUpdate 停止调用时的内存泄漏）
                    long now = System.currentTimeMillis();
                    Iterator<Map.Entry<String, TrackedTarget>> it = trackedTargets.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry<String, TrackedTarget> entry = it.next();
                        if (now - entry.getValue().getLastUpdateTime() > TRACKED_TARGET_EXPIRE_MS) {
                            logger.debug("清理超时跟踪目标: trackingId={}", entry.getKey());
                            trajectories.remove(entry.getKey());
                            it.remove();
                        }
                    }
                } catch (Exception e) {
                    logger.error("清理过期轨迹异常", e);
                }
            }
        }, CLEANUP_INTERVAL_MS, CLEANUP_INTERVAL_MS);
    }

    /**
     * 停止服务
     */
    public void shutdown() {
        if (cleanupTimer != null) {
            cleanupTimer.cancel();
        }
        trajectories.clear();
        trackedTargets.clear();
    }
}
