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
     */
    public List<IntrusionEvent> detectIntrusion(List<Point> currentPoints, DefenseZone zone,
            BackgroundModel background) {
        if (currentPoints == null || currentPoints.isEmpty()) {
            return new ArrayList<>();
        }

        if (zone == null || !zone.getEnabled()) {
            return new ArrayList<>();
        }

        // 1. 过滤：只保留防区内的点
        List<Point> zonePoints = currentPoints.stream()
                .filter(p -> zone.isPointInZone(p))
                .collect(Collectors.toList());

        if (zonePoints.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. 背景差分：从当前点云中移除背景点
        List<Point> intrusionPoints = subtractBackground(zonePoints, background);

        // 3. 将本帧侵入点加入滑动窗口缓冲区
        long now = System.currentTimeMillis();
        String zoneId = zone.getZoneId();
        Deque<TimestampedPoint> buffer = accumulationBuffers
                .computeIfAbsent(zoneId, k -> new ArrayDeque<>());

        // ArrayDeque 不是线程安全的，多线程并发修改会导致内部数组损坏和死循环
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

            // 4. 节流
            Long lastOutput = lastClusterOutputTime.get(zoneId);
            if (lastOutput != null && (now - lastOutput) < CLUSTER_THROTTLE_MS) {
                return null;
            }

            // 5. 提取快照用于聚类（在锁内完成复制，锁外做耗时的聚类运算）
            accumulated = new ArrayList<>(buffer.size());
            for (TimestampedPoint tp : buffer) {
                accumulated.add(tp.point);
            }
        }

        if (accumulated.size() < minPoints) {
            return new ArrayList<>();
        }

        // 6. DBSCAN 聚类
        DBSCAN dbscan = new DBSCAN(eps, minPoints);
        List<List<Point>> clusters = dbscan.cluster(accumulated);

        if (clusters.isEmpty()) {
            return new ArrayList<>();
        }

        lastClusterOutputTime.put(zoneId, now);

        // 7. 特征提取
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
            logger.info("侵入聚类: zoneId={}, 累积点数={}, 聚类数={}, 最大聚类点数={}",
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
     * 
     * @param currentPoints 当前点云
     * @param zones         防区列表
     * @param background    背景模型
     */
    public void markIntruderPoints(List<Point> currentPoints, List<DefenseZone> zones, BackgroundModel background) {
        if (currentPoints == null || currentPoints.isEmpty() || zones == null || zones.isEmpty()) {
            return;
        }

        // 记录调试信息
        long enabledZones = zones.stream().filter(DefenseZone::getEnabled).count();
        if (enabledZones > 0) {
            logger.debug("标记侵入点: 总点数={}, 启用防区数={}", currentPoints.size(), enabledZones);
        }

        // 遍历所有点
        for (Point point : currentPoints) {
            // 对每个点，检查是否在任意已启用的防区内
            for (DefenseZone zone : zones) {
                if (!zone.getEnabled())
                    continue;

                boolean isIntruder = false;

                if (DefenseZone.ZONE_TYPE_BOUNDING_BOX.equals(zone.getZoneType())) {
                    // 边界框检测
                    isIntruder = zone.isPointInZone(point);
                } else if (DefenseZone.ZONE_TYPE_SHRINK.equals(zone.getZoneType())) {
                    // 缩小距离检测 - 使用预计算的径向边界网格 O(1) 查找
                    if (background != null && background.getBoundaryGrid() != null) {
                        isIntruder = background.getBoundaryGrid().isIntrusion(point);
                    }
                }

                if (isIntruder) {
                    point.zoneId = zone.getZoneId();
                    logger.debug("标记侵入点: zoneId={}, point=({}, {}, {})",
                            zone.getZoneId(), String.format("%.2f", point.x), String.format("%.2f", point.y),
                            String.format("%.2f", point.z));
                    break; // 一个点只归属一个防区（优先归属第一个匹配的）
                }
            }
        }
    }
}
