package com.digital.video.gateway.service;

import com.digital.video.gateway.database.Database;
import com.digital.video.gateway.database.RadarIntrusionRecord;
import com.digital.video.gateway.database.RadarIntrusionRecordDAO;
import com.digital.video.gateway.driver.livox.algorithm.DBSCAN;
import com.digital.video.gateway.driver.livox.algorithm.SpatialIndex;
import com.digital.video.gateway.driver.livox.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 侵入检测服务
 * 负责实时检测防区内的侵入物体。
 * 
 * Livox Mid-360 非重复扫描模式下单帧仅约 100 点，入侵前景点更少（通常 &lt; 10），
 * 单帧无法满足 DBSCAN 最小点数要求。因此采用**滑动窗口累积**：
 * 将最近 ACCUMULATION_WINDOW_MS 毫秒内的侵入前景点汇聚后再做聚类。
 */
public class IntrusionDetectionService {
    private static final Logger logger = LoggerFactory.getLogger(IntrusionDetectionService.class);

    private final Database database;
    private final Connection connection;
    private final RadarIntrusionRecordDAO recordDAO;

    // DBSCAN 参数（适配累积后的点密度）
    private final float eps = 1.0f;
    private final int minPoints = 3;

    /** 滑动窗口时长（毫秒），累积此时间段内的侵入点后再聚类 */
    private static final long ACCUMULATION_WINDOW_MS = 800;
    /** 聚类输出节流：同一防区两次聚类输出的最小间隔（毫秒），防止每帧都产生事件 */
    private static final long CLUSTER_THROTTLE_MS = 1000;
    /** 累积缓冲区最大点数，超出时淘汰最旧的点 */
    private static final int MAX_ACCUMULATED_POINTS = 2000;

    /** 按 zoneId 缓存的滑动窗口侵入点 */
    private final Map<String, Deque<TimestampedPoint>> accumulationBuffers = new ConcurrentHashMap<>();
    /** 按 zoneId 记录上次输出聚类事件的时间戳 */
    private final Map<String, Long> lastClusterOutputTime = new ConcurrentHashMap<>();

    /** 按背景缓存 SpatialIndex，避免每帧每防区重建（防区开启时显著降低 CPU、提高点云吞吐） */
    private final Map<String, SpatialIndex> spatialIndexCache = new ConcurrentHashMap<>();

    /** 带时间戳的点，用于滑动窗口淘汰 */
    private static class TimestampedPoint {
        final Point point;
        final long timestampMs;
        TimestampedPoint(Point point, long timestampMs) {
            this.point = point;
            this.timestampMs = timestampMs;
        }
    }

    public IntrusionDetectionService(Database database) {
        this.database = database;
        this.connection = database.getConnection();
        this.recordDAO = new RadarIntrusionRecordDAO(connection);
    }

    /**
     * 检测侵入（滑动窗口累积版）
     * 优化：直接复用 markIntruderPoints 已标记 zoneId 的点，跳过重复的防区过滤和背景差分。
     */
    public List<IntrusionEvent> detectIntrusion(List<Point> currentPoints, DefenseZone zone,
            BackgroundModel background) {
        if (currentPoints == null || currentPoints.isEmpty()) {
            return new ArrayList<>();
        }

        if (zone == null || !zone.getEnabled()) {
            return new ArrayList<>();
        }

        String zoneId = zone.getZoneId();

        // 直接提取已被 markIntruderPoints 标记为本防区的侵入点（无需重复做防区过滤和背景差分）
        List<Point> intrusionPoints = new ArrayList<>();
        for (Point p : currentPoints) {
            if (zoneId.equals(p.zoneId)) {
                intrusionPoints.add(p);
            }
        }

        long now = System.currentTimeMillis();
        Deque<TimestampedPoint> buffer = accumulationBuffers
                .computeIfAbsent(zoneId, k -> new ArrayDeque<>());

        List<Point> accumulated;
        synchronized (buffer) {
            for (Point p : intrusionPoints) {
                buffer.addLast(new TimestampedPoint(p, now));
            }

            // 淘汰过期点
            long cutoff = now - ACCUMULATION_WINDOW_MS;
            while (!buffer.isEmpty() && buffer.peekFirst().timestampMs < cutoff) {
                buffer.pollFirst();
            }
            // 硬上限
            while (buffer.size() > MAX_ACCUMULATED_POINTS) {
                buffer.pollFirst();
            }

            // 节流：防止多线程同时触发 DBSCAN
            Long lastOutput = lastClusterOutputTime.get(zoneId);
            if (lastOutput != null && (now - lastOutput) < CLUSTER_THROTTLE_MS) {
                return null;
            }
            // 在锁内立即占位，阻止其他线程同一时刻也通过节流
            lastClusterOutputTime.put(zoneId, now);

            // 提取快照用于聚类
            accumulated = new ArrayList<>(buffer.size());
            for (TimestampedPoint tp : buffer) {
                accumulated.add(tp.point);
            }
        }

        if (accumulated.size() < minPoints) {
            return new ArrayList<>();
        }

        // DBSCAN 聚类
        DBSCAN dbscan = new DBSCAN(eps, minPoints);
        List<List<Point>> clusters = dbscan.cluster(accumulated);

        if (clusters.isEmpty()) {
            return new ArrayList<>();
        }

        // 特征提取
        List<IntrusionEvent> events = new ArrayList<>();
        for (int i = 0; i < clusters.size(); i++) {
            List<Point> clusterPoints = clusters.get(i);
            PointCluster cluster = new PointCluster("cluster_" + now + "_" + i, clusterPoints);
            cluster.calculateFeatures();

            IntrusionEvent event = new IntrusionEvent();
            event.setRecordId("rec_" + now + "_" + UUID.randomUUID().toString().substring(0, 8));
            event.setDeviceId(zone.getDeviceId());
            event.setZoneId(zone.getZoneId());
            event.setCluster(cluster);
            event.setClusterId(cluster.getClusterId());

            events.add(event);
        }

        if (!events.isEmpty()) {
            logger.debug("侵入聚类: zoneId={}, 累积点数={}, 聚类数={}, 最大聚类点数={}",
                    zoneId, accumulated.size(), events.size(),
                    events.stream().mapToInt(e -> e.getCluster().getPointCount()).max().orElse(0));
        }

        return events;
    }

    /**
     * 背景差分：从当前点云中移除背景点
     * 使用按背景缓存的 SpatialIndex，避免每帧每防区重建，提高检测模式下的点云吞吐。
     */
    private List<Point> subtractBackground(List<Point> currentPoints, BackgroundModel background) {
        if (background == null) {
            return new ArrayList<>(currentPoints);
        }

        SpatialIndex spatialIndex = getOrBuildSpatialIndex(background);

        // 过滤：只保留不在背景中的点
        List<Point> newPoints = new ArrayList<>();
        for (Point point : currentPoints) {
            if (!spatialIndex.isPointInBackground(point, 0.1f)) {
                newPoints.add(point);
            }
        }

        return newPoints;
    }

    /** 清除空间索引缓存，在防区/背景变更后调用以强制下次重建 */
    public void clearSpatialIndexCache() {
        spatialIndexCache.clear();
    }

    private SpatialIndex getOrBuildSpatialIndex(BackgroundModel background) {
        String key = background.getBackgroundId();
        if (key == null || key.isEmpty()) {
            key = "bg_" + System.identityHashCode(background);
        }
        SpatialIndex cached = spatialIndexCache.get(key);
        if (cached != null) {
            return cached;
        }
        SpatialIndex spatialIndex = new SpatialIndex(background.getGridResolution());
        for (BackgroundPoint bgPoint : background.getPoints()) {
            spatialIndex.addPoint(bgPoint);
        }
        spatialIndexCache.put(key, spatialIndex);
        return spatialIndex;
    }

    /**
     * 保存侵入记录到数据库
     */
    private void saveIntrusionRecord(IntrusionEvent event) {
        try {
            RadarIntrusionRecord record = new RadarIntrusionRecord();
            record.setRecordId(event.getRecordId());
            record.setDeviceId(event.getDeviceId());
            record.setAssemblyId(event.getAssemblyId());
            record.setZoneId(event.getZoneId());
            record.setClusterId(event.getClusterId());

            PointCluster cluster = event.getCluster();
            if (cluster != null && cluster.getCentroid() != null) {
                record.setCentroidX(cluster.getCentroid().x);
                record.setCentroidY(cluster.getCentroid().y);
                record.setCentroidZ(cluster.getCentroid().z);
                record.setVolume(cluster.getVolume());
                record.setPointCount(cluster.getPointCount());

                if (cluster.getBbox() != null) {
                    record.setBboxMinX(cluster.getBbox().minX);
                    record.setBboxMinY(cluster.getBbox().minY);
                    record.setBboxMinZ(cluster.getBbox().minZ);
                    record.setBboxMaxX(cluster.getBbox().maxX);
                    record.setBboxMaxY(cluster.getBbox().maxY);
                    record.setBboxMaxZ(cluster.getBbox().maxZ);
                }
            }

            record.setDetectedAt(event.getDetectedAt());
            recordDAO.save(record);
        } catch (Exception e) {
            logger.error("保存侵入记录失败: {}", event.getRecordId(), e);
        }
    }

    /**
     * 获取侵入记录列表
     */
    public List<RadarIntrusionRecord> getIntrusionRecords(String deviceId, String zoneId,
            Date startTime, Date endTime,
            int page, int pageSize) {
        return recordDAO.getRecords(deviceId, zoneId, startTime, endTime, page, pageSize);
    }

    /**
     * 清空侵入记录
     */
    public int clearIntrusionRecords(String deviceId) {
        return recordDAO.deleteAll(deviceId);
    }

    /**
     * 标记侵入点（直接修改 Point 对象的 zoneId）
     * 双重过滤：RadialBoundaryGrid O(1) 空间判定 + SpatialIndex 背景差分，消除噪声/抖动导致的假阳性。
     *
     * @param currentPoints 当前点云
     * @param zones         防区列表
     * @param background    背景模型
     */
    /** 背景减除搜索半径（米），取收缩距离的 3/4 以覆盖边界噪声 */
    private static final float BG_SUBTRACT_RADIUS = 0.15f;
    /** 单帧孤立点过滤：标记后的侵入点需在此半径内有至少 MIN_NEIGHBORS 个同帧侵入点才保留 */
    private static final float ISOLATION_RADIUS = 0.5f;
    private static final int MIN_NEIGHBORS = 1;

    public void markIntruderPoints(List<Point> currentPoints, List<DefenseZone> zones, BackgroundModel background) {
        if (currentPoints == null || currentPoints.isEmpty() || zones == null || zones.isEmpty()) {
            return;
        }

        if (logger.isDebugEnabled()) {
            long enabledZones = 0;
            for (DefenseZone z : zones) {
                if (z.getEnabled()) enabledZones++;
            }
            if (enabledZones > 0) {
                logger.debug("标记侵入点: 总点数={}, 启用防区数={}", currentPoints.size(), enabledZones);
            }
        }

        SpatialIndex spatialIndex = (background != null) ? getOrBuildSpatialIndex(background) : null;

        // 第一遍：初步标记
        for (Point point : currentPoints) {
            for (DefenseZone zone : zones) {
                if (!zone.getEnabled())
                    continue;

                boolean isIntruder = false;

                if (DefenseZone.ZONE_TYPE_BOUNDING_BOX.equals(zone.getZoneType())) {
                    isIntruder = zone.isPointInZone(point);
                } else if (DefenseZone.ZONE_TYPE_SHRINK.equals(zone.getZoneType())) {
                    if (background != null && background.getBoundaryGrid() != null) {
                        isIntruder = background.getBoundaryGrid().isIntrusion(point);
                    }
                }

                if (isIntruder && spatialIndex != null) {
                    if (spatialIndex.isPointInBackground(point, BG_SUBTRACT_RADIUS)) {
                        isIntruder = false;
                    }
                }

                if (isIntruder) {
                    point.zoneId = zone.getZoneId();
                    break;
                }
            }
        }

        // 第二遍：孤立点过滤 — 移除没有近邻的噪声标记
        List<Point> marked = new ArrayList<>();
        for (Point p : currentPoints) {
            if (p.zoneId != null) marked.add(p);
        }
        if (marked.size() > 0 && marked.size() <= 500) {
            float r2 = ISOLATION_RADIUS * ISOLATION_RADIUS;
            for (Point p : marked) {
                int neighbors = 0;
                for (Point q : marked) {
                    if (q == p) continue;
                    float dx = p.x - q.x, dy = p.y - q.y, dz = p.z - q.z;
                    if (dx * dx + dy * dy + dz * dz <= r2) {
                        neighbors++;
                        if (neighbors >= MIN_NEIGHBORS) break;
                    }
                }
                if (neighbors < MIN_NEIGHBORS) {
                    p.zoneId = null;
                }
            }
        }
    }
}
