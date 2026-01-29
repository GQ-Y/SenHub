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
 * 负责实时检测防区内的侵入物体
 */
public class IntrusionDetectionService {
    private static final Logger logger = LoggerFactory.getLogger(IntrusionDetectionService.class);

    private final Database database;
    private final Connection connection;
    private final RadarIntrusionRecordDAO recordDAO;

    // DBSCAN参数
    private final float eps = 0.3f; // 邻域半径（米）
    private final int minPoints = 5; // 最小点数

    /** 按背景缓存 SpatialIndex，避免每帧每防区重建（防区开启时显著降低 CPU、提高点云吞吐） */
    private final Map<String, SpatialIndex> spatialIndexCache = new ConcurrentHashMap<>();

    public IntrusionDetectionService(Database database) {
        this.database = database;
        this.connection = database.getConnection();
        this.recordDAO = new RadarIntrusionRecordDAO(connection);
    }

    /**
     * 检测侵入
     * 
     * @param currentPoints 当前点云
     * @param zone          防区配置
     * @param background    背景模型
     * @return 侵入事件列表
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

        if (intrusionPoints.isEmpty()) {
            return new ArrayList<>();
        }

        // 3. 聚类：对侵入点进行DBSCAN聚类
        DBSCAN dbscan = new DBSCAN(eps, minPoints);
        List<List<Point>> clusters = dbscan.cluster(intrusionPoints);

        // 4. 特征提取：为每个聚类提取特征
        List<IntrusionEvent> events = new ArrayList<>();
        for (int i = 0; i < clusters.size(); i++) {
            List<Point> clusterPoints = clusters.get(i);
            PointCluster cluster = new PointCluster("cluster_" + System.currentTimeMillis() + "_" + i, clusterPoints);
            cluster.calculateFeatures();

            IntrusionEvent event = new IntrusionEvent();
            event.setRecordId("rec_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8));
            event.setDeviceId(zone.getDeviceId());
            event.setZoneId(zone.getZoneId());
            event.setCluster(cluster);
            event.setClusterId(cluster.getClusterId());

            events.add(event);
        }

        // 注意：侵入记录改由 RecordingManager 统一保存，避免重复
        // 如果需要单独的事件记录，可以在此处添加额外逻辑
        // for (IntrusionEvent event : events) {
        // saveIntrusionRecord(event);
        // }

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
