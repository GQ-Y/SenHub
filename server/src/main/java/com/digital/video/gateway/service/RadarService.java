package com.digital.video.gateway.service;

import com.digital.video.gateway.driver.livox.LivoxDriver;
import com.digital.video.gateway.driver.livox.protocol.SdkPacket;
import com.digital.video.gateway.driver.livox.model.*;
import com.digital.video.gateway.driver.livox.algorithm.CoordinateTransform;
import com.digital.video.gateway.database.Database;
import com.digital.video.gateway.database.RadarDeviceDAO;
import com.digital.video.gateway.api.RadarWebSocketHandler;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 览沃 (Livox) Mid-360 雷达服务
 * 负责接收 UDP 点云数据，解析目标位置，并驱动摄像头 PTZ 联动。
 * 集成背景建模、防区管理、侵入检测功能。
 */
public class RadarService {
    private static final Logger logger = LoggerFactory.getLogger(RadarService.class);

    private final PTZService ptzService;
    private final LivoxDriver livoxDriver; // JNI Driver
    private final Database database;
    
    // 集成服务
    private final BackgroundModelService backgroundService;
    private final DefenseZoneService defenseZoneService;
    private final IntrusionDetectionService intrusionDetectionService;
    
    // WebSocket处理器（用于实时推送）
    private RadarWebSocketHandler webSocketHandler;
    
    // 设备状态管理（deviceId -> 状态）
    private final Map<String, String> deviceStates = new ConcurrentHashMap<>(); // "collecting" 或 "detecting"
    private final Map<String, BackgroundModel> loadedBackgrounds = new ConcurrentHashMap<>(); // deviceId -> BackgroundModel
    private final Map<String, List<DefenseZone>> deviceZones = new ConcurrentHashMap<>(); // deviceId -> List<DefenseZone>

    // 点云数据统计
    private final AtomicLong totalPointCloudFrames = new AtomicLong(0);
    private final AtomicLong totalPointCloudPoints = new AtomicLong(0);
    private ScheduledExecutorService statsExecutor;

    public RadarService(PTZService ptzService, Database database) {
        this.ptzService = ptzService;
        this.database = database;
        this.livoxDriver = new LivoxDriver(database);
        this.backgroundService = new BackgroundModelService(database);
        this.defenseZoneService = new DefenseZoneService(database);
        this.intrusionDetectionService = new IntrusionDetectionService(database);
        this.webSocketHandler = new RadarWebSocketHandler(this);
    }
    
    /**
     * 获取WebSocket处理器（供外部调用）
     */
    public RadarWebSocketHandler getWebSocketHandler() {
        return webSocketHandler;
    }

    /**
     * 启动雷达监听
     */
    public synchronized void start() {
        try {
            livoxDriver.start();
            livoxDriver.setPointCloudCallback(this::handlePacket);
            logger.info("雷达监听服务已启动 (JNI Driver)");
            
            // 启动统计信息打印任务（每秒打印一次）
            startStatsReporter();
        } catch (Exception e) {
            logger.error("启动雷达监听服务失败", e);
        }
    }
    
    /**
     * 启动统计信息报告器（每秒打印一次）
     */
    private void startStatsReporter() {
        statsExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "RadarService-StatsReporter");
            t.setDaemon(true);
            return t;
        });
        
        final AtomicLong lastTotalFrames = new AtomicLong(0);
        final AtomicLong lastTotalPoints = new AtomicLong(0);
        
        statsExecutor.scheduleAtFixedRate(() -> {
            long currentFrames = totalPointCloudFrames.get();
            long currentPoints = totalPointCloudPoints.get();
            
            long framesThisSecond = currentFrames - lastTotalFrames.get();
            long pointsThisSecond = currentPoints - lastTotalPoints.get();
            
            lastTotalFrames.set(currentFrames);
            lastTotalPoints.set(currentPoints);
            
            // 每秒都打印，即使没有新数据也显示累计统计
            logger.info("[点云统计] 本秒接收: {} 帧, {} 点 | 累计: {} 帧, {} 点", 
                framesThisSecond, pointsThisSecond, currentFrames, currentPoints);
        }, 1, 1, TimeUnit.SECONDS);
        
        logger.info("点云统计报告器已启动（每秒打印一次）");
    }

    /**
     * 停止雷达监听
     */
    public synchronized void stop() {
        // 停止统计报告器
        if (statsExecutor != null) {
            statsExecutor.shutdown();
            try {
                if (!statsExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    statsExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                statsExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        livoxDriver.stop();
        logger.info("雷达监听服务已停止");
    }

    /**
     * 解析 Livox 数据包
     */
    private void handlePacket(SdkPacket packet) {
        if (packet.payload == null || packet.payload.length < 1)
            return;

        ByteBuffer bb = ByteBuffer.wrap(packet.payload).order(ByteOrder.LITTLE_ENDIAN);
        byte dataType = bb.get();

        // Mid-360 常见的笛卡尔坐标数据类型 0x01
        if (dataType == 0x01) {
            List<Point> points = parseCartesianPoints(bb);
            
            // 更新统计信息
            if (!points.isEmpty()) {
                totalPointCloudFrames.incrementAndGet();
                totalPointCloudPoints.addAndGet(points.size());
                
                // 根据设备状态路由点云数据
                // 注意：由于点云回调中只有handle，这里简化处理
                // 如果只有一个雷达设备，直接使用；多个设备时使用第一个
                String deviceId = getCurrentDeviceId();
                if (deviceId != null) {
                    routePointCloud(deviceId, points);
                } else {
                    // 没有配置雷达设备，忽略点云数据
                    logger.debug("没有配置雷达设备，忽略点云数据");
                }
            }
        }
    }

    /**
     * 解析笛卡尔坐标点云 (Type 0x01)
     * 每个点 13 字节: x(4), y(4), z(4), reflectivity(1)
     */
    private List<Point> parseCartesianPoints(ByteBuffer bb) {
        List<Point> points = new ArrayList<>();
        
        while (bb.remaining() >= 13) {
            int xInt = bb.getInt();
            int yInt = bb.getInt();
            int zInt = bb.getInt();
            byte reflectivity = bb.get();

            float x = xInt / 1000.0f; // mm -> m
            float y = yInt / 1000.0f;
            float z = zInt / 1000.0f;

            // 简单过滤：只保留 0.5m 到 30m 范围内的目标
            float distance = (float) Math.sqrt(x * x + y * y + z * z);
            if (distance > 0.5f && distance < 30.0f) {
                points.add(new Point(x, y, z, reflectivity));
            }
        }
        
        return points;
    }

    /**
     * 路由点云数据（根据设备状态）
     */
    private void routePointCloud(String deviceId, List<Point> points) {
        if (deviceId == null) {
            // 没有配置雷达设备，忽略点云数据
            return;
        }

        String state = deviceStates.get(deviceId);
        if ("collecting".equals(state)) {
            // 采集背景模式：添加到背景采集
            PointCloudFrame frame = new PointCloudFrame(System.currentTimeMillis(), points);
            backgroundService.addFrame(deviceId, frame);
            // 实时推送点云数据（用于前端实时预览）
            if (webSocketHandler != null) {
                webSocketHandler.pushPointCloud(deviceId, points);
                webSocketHandler.logPushStats(deviceId); // 记录推送日志
            }
        } else if ("detecting".equals(state)) {
            // 检测模式：进行侵入检测
            List<IntrusionEvent> events = processDetection(deviceId, points);
            // 实时推送点云数据
            if (webSocketHandler != null) {
                webSocketHandler.pushPointCloud(deviceId, points);
                webSocketHandler.logPushStats(deviceId); // 记录推送日志
            }
            // 推送侵入检测结果
            if (webSocketHandler != null && !events.isEmpty()) {
                for (IntrusionEvent event : events) {
                    Map<String, Object> eventData = new HashMap<>();
                    eventData.put("zoneId", event.getZoneId());
                    eventData.put("detectedAt", new java.util.Date());
                    if (event.getCluster() != null) {
                        PointCluster cluster = event.getCluster();
                        if (cluster.getCentroid() != null) {
                            Map<String, Object> centroid = new HashMap<>();
                            centroid.put("x", cluster.getCentroid().x);
                            centroid.put("y", cluster.getCentroid().y);
                            centroid.put("z", cluster.getCentroid().z);
                            eventData.put("centroid", centroid);
                        }
                        eventData.put("volume", cluster.getVolume());
                        if (cluster.getPoints() != null) {
                            eventData.put("pointCount", cluster.getPoints().size());
                            List<Map<String, Object>> clusterPoints = new ArrayList<>();
                            for (Point p : cluster.getPoints()) {
                                Map<String, Object> pointMap = new HashMap<>();
                                pointMap.put("x", p.x);
                                pointMap.put("y", p.y);
                                pointMap.put("z", p.z);
                                clusterPoints.add(pointMap);
                            }
                            Map<String, Object> clusterData = new HashMap<>();
                            clusterData.put("points", clusterPoints);
                            eventData.put("cluster", clusterData);
                        }
                    }
                    webSocketHandler.pushIntrusion(deviceId, eventData);
                }
            }
        } else {
            // 默认模式：实时推送点云数据
            if (webSocketHandler != null) {
                webSocketHandler.pushPointCloud(deviceId, points);
                webSocketHandler.logPushStats(deviceId); // 记录推送日志
            }
        }
    }

    /**
     * 处理侵入检测
     * @return 侵入事件列表
     */
    private List<IntrusionEvent> processDetection(String deviceId, List<Point> currentPoints) {
        BackgroundModel background = loadedBackgrounds.get(deviceId);
        if (background == null) {
            return new ArrayList<>(); // 背景未加载
        }

        List<DefenseZone> zones = deviceZones.get(deviceId);
        if (zones == null || zones.isEmpty()) {
            return new ArrayList<>(); // 无防区配置
        }

        List<IntrusionEvent> allEvents = new ArrayList<>();
        
        // 对每个启用的防区进行检测
        for (DefenseZone zone : zones) {
            if (!zone.getEnabled()) {
                continue;
            }

            List<IntrusionEvent> events = intrusionDetectionService.detectIntrusion(currentPoints, zone, background);
            allEvents.addAll(events);
            
            // 处理侵入事件：触发PTZ联动
            for (IntrusionEvent event : events) {
                handleIntrusionEvent(event, zone);
            }
        }
        
        return allEvents;
    }

    /**
     * 处理侵入事件：PTZ联动
     */
    private void handleIntrusionEvent(IntrusionEvent event, DefenseZone zone) {
        PointCluster cluster = event.getCluster();
        if (cluster == null || cluster.getCentroid() == null) {
            return;
        }

        Point radarPoint = cluster.getCentroid();
        
        // 坐标系转换
        DefenseZone.CoordinateTransform transform = zone.getCoordinateTransform();
        com.digital.video.gateway.driver.livox.algorithm.CoordinateTransform coordTransform = 
            new com.digital.video.gateway.driver.livox.algorithm.CoordinateTransform(
                transform.translationX, transform.translationY, transform.translationZ,
                transform.rotationX, transform.rotationY, transform.rotationZ,
                transform.scale
            );
        
        Point cameraPoint = coordTransform.transformRadarToCamera(radarPoint);
        
        // 计算PTZ角度
        float[] angles = coordTransform.calculatePTZAngles(cameraPoint);
        float pan = angles[0];
        float tilt = angles[1];

        // 驱动PTZ
        if (zone.getCameraDeviceId() != null) {
            int channel = zone.getCameraChannel() != null ? zone.getCameraChannel() : 1;
            ptzService.gotoAngle(zone.getCameraDeviceId(), channel, pan, tilt, 1.0f);
            logger.info("侵入检测触发PTZ联动: zoneId={}, camera={}, pan={}, tilt={}", 
                    zone.getZoneId(), zone.getCameraDeviceId(), pan, tilt);
        }
    }

    /**
     * 获取当前设备ID（从数据库读取）
     * 注意：由于点云回调中只有handle，没有设备IP信息，这里简化处理
     * 如果只有一个雷达设备，直接使用；多个设备时使用第一个
     */
    private String getCurrentDeviceId() {
        // 从数据库读取所有雷达设备
        RadarDeviceDAO radarDeviceDAO = new RadarDeviceDAO(database.getConnection());
        List<com.digital.video.gateway.database.RadarDevice> devices = radarDeviceDAO.getAll();
        
        if (devices.isEmpty()) {
            // 没有雷达设备，返回null
            return null;
        } else if (devices.size() == 1) {
            // 只有一个雷达设备，直接使用
            return devices.get(0).getDeviceId();
        } else {
            // 多个雷达设备，优先使用配置指定的，否则使用第一个
            String configuredDeviceId = database.getConfig("radar.current_device_id");
            if (configuredDeviceId != null && !configuredDeviceId.trim().isEmpty()) {
                // 检查配置的设备ID是否存在
                for (com.digital.video.gateway.database.RadarDevice device : devices) {
                    if (configuredDeviceId.equals(device.getDeviceId())) {
                        return device.getDeviceId();
                    }
                }
            }
            // 使用第一个设备
            logger.debug("多个雷达设备，使用第一个: {}", devices.get(0).getDeviceId());
            return devices.get(0).getDeviceId();
        }
    }
    
    /**
     * 根据handle获取设备ID（简化实现：如果只有一个设备，直接返回）
     * TODO: 未来可以通过JNI扩展获取handle对应的设备IP/SN进行精确匹配
     */
    private String getDeviceIdByHandle(int handle) {
        // 简化实现：返回第一个设备的ID
        // 实际应该通过handle查询设备信息进行匹配
        return getCurrentDeviceId();
    }

    // ==================== 公共API方法 ====================

    /**
     * 开始采集背景
     */
    public String startBackgroundCollection(String deviceId, int durationSeconds, float gridResolution) {
        deviceStates.put(deviceId, "collecting");
        return backgroundService.startCollection(deviceId, durationSeconds, gridResolution);
    }

    /**
     * 停止采集背景
     */
    public String stopBackgroundCollection(String deviceId) {
        String backgroundId = backgroundService.stopCollection(deviceId);
        if (backgroundId != null) {
            deviceStates.put(deviceId, "detecting");
            // 加载背景模型
            BackgroundModel background = backgroundService.loadBackground(backgroundId);
            if (background != null) {
                loadedBackgrounds.put(deviceId, background);
            }
            // 加载防区配置
            loadZonesForDevice(deviceId);
        }
        return backgroundId;
    }

    /**
     * 加载设备的防区配置
     */
    private void loadZonesForDevice(String deviceId) {
        List<DefenseZone> zones = defenseZoneService.getZonesByDeviceId(deviceId);
        deviceZones.put(deviceId, zones);
    }

    /**
     * 设置设备状态
     */
    public void setDeviceState(String deviceId, String state) {
        deviceStates.put(deviceId, state);
    }
}
