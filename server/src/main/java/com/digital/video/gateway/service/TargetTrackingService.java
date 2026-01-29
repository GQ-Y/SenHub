package com.digital.video.gateway.service;

import com.digital.video.gateway.driver.livox.model.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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

    // 目标轨迹存储：targetId -> TargetTrajectory
    private final Map<String, TargetTrajectory> trajectories = new ConcurrentHashMap<>();
    
    // 清理过期轨迹的定时任务
    private Timer cleanupTimer;
    private static final long CLEANUP_INTERVAL_MS = 2000; // 每2秒清理一次

    public TargetTrackingService() {
        startCleanupTimer();
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
    private void startCleanupTimer() {
        cleanupTimer = new Timer("TargetTrackingCleanup", true);
        cleanupTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    long currentTime = System.currentTimeMillis();
                    List<String> expiredTargets = new ArrayList<>();
                    
                    for (Map.Entry<String, TargetTrajectory> entry : trajectories.entrySet()) {
                        if (entry.getValue().isExpired()) {
                            expiredTargets.add(entry.getKey());
                        }
                    }
                    
                    for (String targetId : expiredTargets) {
                        trajectories.remove(targetId);
                        logger.debug("清理过期目标轨迹: targetId={}", targetId);
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
    }
}
