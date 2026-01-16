package com.digital.video.gateway.service;

import com.digital.video.gateway.driver.livox.LivoxDriver;
import com.digital.video.gateway.driver.livox.protocol.SdkPacket;
import com.digital.video.gateway.driver.livox.model.*;
import com.digital.video.gateway.driver.livox.algorithm.CoordinateTransform;
import com.digital.video.gateway.driver.livox.algorithm.PointCloudProcessor;
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
    private final RecordingManager recordingManager; // 侵入录制管理器

    // WebSocket处理器（用于实时推送）
    private RadarWebSocketHandler webSocketHandler;

    // 设备状态管理（deviceId -> 状态）
    private final Map<String, String> deviceStates = new ConcurrentHashMap<>(); // "collecting" 或 "detecting"
    private final Map<String, BackgroundModel> loadedBackgrounds = new ConcurrentHashMap<>(); // deviceId ->
                                                                                              // BackgroundModel
    private final Map<String, List<DefenseZone>> deviceZones = new ConcurrentHashMap<>(); // deviceId ->
                                                                                          // List<DefenseZone>

    // 点云数据统计
    private final AtomicLong totalPointCloudFrames = new AtomicLong(0);
    private final AtomicLong totalPointCloudPoints = new AtomicLong(0);
    private ScheduledExecutorService statsExecutor;

    // 降噪配置参数
    private boolean enableDenoising = true; // 默认启用降噪
    private int denoiseKNeighbors = 20; // K近邻数量
    private float denoiseStdDevThreshold = 1.0f; // 标准差阈值
    private boolean enableVoxelDownsample = false; // 默认不启用下采样
    private float voxelResolution = 0.05f; // 下采样分辨率（5cm）

    public RadarService(PTZService ptzService, Database database) {
        this.ptzService = ptzService;
        this.database = database;
        this.livoxDriver = new LivoxDriver(database);
        this.backgroundService = new BackgroundModelService(database);
        this.defenseZoneService = new DefenseZoneService(database);
        this.intrusionDetectionService = new IntrusionDetectionService(database);
        this.recordingManager = new RecordingManager(database);
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

            // 服务启动时自动加载所有设备的检测上下文（恢复检测状态）
            loadAllDeviceDetectionContexts();
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

        // 重要修复：dataType 应从 packet.cmdId 获取，而不是从 payload 读取
        // JNI 回调中的 dataType 被存储在 packet.cmdId 中
        int dataType = packet.cmdId;

        // Mid-360 常见的笛卡尔坐标数据类型
        // 0x01 = 高精度笛卡尔坐标 (每点14字节: x4+y4+z4+reflectivity1+tag1)
        // 0x02 = 低精度笛卡尔坐标 (每点8字节: x2+y2+z2+reflectivity1+tag1, 单位cm)
        if (dataType == 0x01) {
            // 高精度笛卡尔坐标
            List<Point> points = parseCartesianHighPoints(packet.payload);

            // 更新统计信息
            if (!points.isEmpty()) {
                totalPointCloudFrames.incrementAndGet();
                totalPointCloudPoints.addAndGet(points.size());

                // 根据设备状态路由点云数据
                String deviceId = getCurrentDeviceId();
                if (deviceId != null) {
                    routePointCloud(deviceId, points);
                } else {
                    logger.debug("没有配置雷达设备，忽略点云数据");
                }
            }
        } else if (dataType == 0x02) {
            // 低精度笛卡尔坐标
            List<Point> points = parseCartesianLowPoints(packet.payload);

            if (!points.isEmpty()) {
                totalPointCloudFrames.incrementAndGet();
                totalPointCloudPoints.addAndGet(points.size());

                String deviceId = getCurrentDeviceId();
                if (deviceId != null) {
                    routePointCloud(deviceId, points);
                }
            }
        } else {
            logger.debug("不支持的点云数据类型: 0x{}", String.format("%02X", dataType));
        }
    }

    /**
     * 解析高精度笛卡尔坐标点云 (Type 0x01)
     * 根据 Livox SDK2 定义: LivoxLidarCartesianHighRawPoint
     * 每个点 14 字节: x(int32/4), y(int32/4), z(int32/4), reflectivity(uint8/1),
     * tag(uint8/1)
     * 坐标单位: 毫米(mm)
     */
    private List<Point> parseCartesianHighPoints(byte[] data) {
        List<Point> points = new ArrayList<>();
        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        // 每个点 14 字节
        while (bb.remaining() >= 14) {
            int xInt = bb.getInt(); // 4 bytes, mm
            int yInt = bb.getInt(); // 4 bytes, mm
            int zInt = bb.getInt(); // 4 bytes, mm
            byte reflectivity = bb.get(); // 1 byte
            byte tag = bb.get(); // 1 byte (忽略)

            // mm -> m
            float x = xInt / 1000.0f;
            float y = yInt / 1000.0f;
            float z = zInt / 1000.0f;

            // 可选：过滤无效点（tag 的某些位表示无效点）
            // Mid-360 的 tag 定义：bit0-1 表示置信度，bit2 表示是否有效
            // 这里暂时不过滤，发送全部点

            points.add(new Point(x, y, z, reflectivity));
        }

        return points;
    }

    /**
     * 解析低精度笛卡尔坐标点云 (Type 0x02)
     * 根据 Livox SDK2 定义: LivoxLidarCartesianLowRawPoint
     * 每个点 8 字节: x(int16/2), y(int16/2), z(int16/2), reflectivity(uint8/1),
     * tag(uint8/1)
     * 坐标单位: 厘米(cm)
     */
    private List<Point> parseCartesianLowPoints(byte[] data) {
        List<Point> points = new ArrayList<>();
        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        // 每个点 8 字节
        while (bb.remaining() >= 8) {
            short xShort = bb.getShort(); // 2 bytes, cm
            short yShort = bb.getShort(); // 2 bytes, cm
            short zShort = bb.getShort(); // 2 bytes, cm
            byte reflectivity = bb.get(); // 1 byte
            byte tag = bb.get(); // 1 byte (忽略)

            // cm -> m
            float x = xShort / 100.0f;
            float y = yShort / 100.0f;
            float z = zShort / 100.0f;

            points.add(new Point(x, y, z, reflectivity));
        }

        return points;
    }

    /**
     * 路由点云数据（根据设备状态）
     * 增强：添加可选的降噪处理
     */
    private void routePointCloud(String deviceId, List<Point> points) {
        if (deviceId == null) {
            // 没有配置雷达设备，忽略点云数据
            return;
        }

        // 降噪处理（可选）
        List<Point> processedPoints = points;

        if (enableDenoising && points.size() > denoiseKNeighbors) {
            try {
                // 统计去噪：移除离群较远的噪声点
                processedPoints = PointCloudProcessor.statisticalOutlierRemoval(
                        processedPoints,
                        denoiseKNeighbors,
                        denoiseStdDevThreshold);

                // 记录降噪效果（仅在调试级别）
                if (logger.isDebugEnabled()) {
                    int removedCount = points.size() - processedPoints.size();
                    logger.debug("降噪处理: 原始点数={}, 处理后={}, 移除={}",
                            points.size(), processedPoints.size(), removedCount);
                }
            } catch (Exception e) {
                logger.warn("降噪处理失败，使用原始数据", e);
                processedPoints = points;
            }
        }

        // 下采样处理（可选，用于减少点云数量）
        if (enableVoxelDownsample && processedPoints.size() > 1000) {
            try {
                processedPoints = PointCloudProcessor.voxelDownsample(
                        processedPoints,
                        voxelResolution);
            } catch (Exception e) {
                logger.warn("下采样处理失败", e);
            }
        }

        String state = deviceStates.get(deviceId);
        if ("collecting".equals(state)) {
            // 采集背景模式：添加到背景采集
            PointCloudFrame frame = new PointCloudFrame(System.currentTimeMillis(), processedPoints);
            backgroundService.addFrame(deviceId, frame);
            // 实时推送点云数据（用于前端实时预览）
            if (webSocketHandler != null) {
                webSocketHandler.pushPointCloud(deviceId, processedPoints);
                webSocketHandler.logPushStats(deviceId); // 记录推送日志
            }
        } else if ("detecting".equals(state)) {
            // 检测模式：进行侵入检测

            // 1. 标记侵入点（用于前端显示红色，以及录制判断）
            BackgroundModel background = loadedBackgrounds.get(deviceId);
            List<DefenseZone> zones = deviceZones.get(deviceId);
            if (background != null && zones != null) {
                int beforeCount = processedPoints.size();
                intrusionDetectionService.markIntruderPoints(processedPoints, zones, background);
                long intruderCount = processedPoints.stream().filter(p -> p.zoneId != null).count();
                if (intruderCount > 0) {
                    logger.debug("检测到侵入点: deviceId={}, 总点数={}, 侵入点数={}", deviceId, beforeCount, intruderCount);
                }
            } else {
                if (background == null) {
                    logger.debug("检测模式但背景模型未加载: deviceId={}", deviceId);
                }
                if (zones == null || zones.isEmpty()) {
                    logger.debug("检测模式但防区未配置: deviceId={}", deviceId);
                }
            }

            // 2. 处理录制（缓冲和保存侵入片段）
            recordingManager.processFrame(deviceId, processedPoints);

            // 3. 生成侵入事件（用于报警和PTZ）
            List<IntrusionEvent> events = processDetection(deviceId, processedPoints);
            // 实时推送点云数据
            if (webSocketHandler != null) {
                webSocketHandler.pushPointCloud(deviceId, processedPoints);
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
                webSocketHandler.pushPointCloud(deviceId, processedPoints);
                webSocketHandler.logPushStats(deviceId); // 记录推送日志
            }
        }
    }

    /**
     * 处理侵入检测
     * 
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
        com.digital.video.gateway.driver.livox.algorithm.CoordinateTransform coordTransform = new com.digital.video.gateway.driver.livox.algorithm.CoordinateTransform(
                transform.translationX, transform.translationY, transform.translationZ,
                transform.rotationX, transform.rotationY, transform.rotationZ,
                transform.scale);

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
     * 加载所有设备的检测上下文（用于服务启动时恢复检测状态）
     * 遍历数据库中所有雷达设备，自动加载防区配置和背景模型
     */
    private void loadAllDeviceDetectionContexts() {
        try {
            RadarDeviceDAO radarDeviceDAO = new RadarDeviceDAO(database.getConnection());
            List<com.digital.video.gateway.database.RadarDevice> devices = radarDeviceDAO.getAll();

            if (devices.isEmpty()) {
                logger.info("服务启动: 没有配置雷达设备，跳过检测上下文加载");
                return;
            }

            logger.info("服务启动: 开始加载 {} 个雷达设备的检测上下文", devices.size());

            for (com.digital.video.gateway.database.RadarDevice device : devices) {
                String deviceId = device.getDeviceId();
                try {
                    logger.info("服务启动: 加载设备检测上下文 deviceId={}", deviceId);
                    reloadDeviceDetectionContext(deviceId);
                } catch (Exception e) {
                    logger.error("服务启动: 加载设备检测上下文失败 deviceId={}", deviceId, e);
                }
            }

            logger.info("服务启动: 检测上下文加载完成，已加载 {} 个设备", devices.size());
        } catch (Exception e) {
            logger.error("服务启动: 加载检测上下文失败", e);
        }
    }

    /**
     * 加载设备的防区配置（内部方法）
     */
    private void loadZonesForDevice(String deviceId) {
        List<DefenseZone> zones = defenseZoneService.getZonesByDeviceId(deviceId);
        deviceZones.put(deviceId, zones);
        logger.info("加载防区配置: deviceId={}, 防区数={}", deviceId, zones != null ? zones.size() : 0);
    }

    /**
     * 重新加载设备的检测上下文（背景模型和防区配置）
     * 在创建、更新、删除防区后调用此方法
     */
    public void reloadDeviceDetectionContext(String deviceId) {
        // 1. 重新加载防区配置
        loadZonesForDevice(deviceId);

        // 2. 查找防区中使用的背景，并加载
        List<DefenseZone> zones = deviceZones.get(deviceId);
        if (zones != null && !zones.isEmpty()) {
            // 取第一个启用的防区的背景ID和shrinkDistance（假设一个设备使用一个背景）
            DefenseZone shrinkZone = zones.stream()
                    .filter(DefenseZone::getEnabled)
                    .filter(z -> z.getBackgroundId() != null && !z.getBackgroundId().isEmpty())
                    .findFirst()
                    .orElse(null);

            if (shrinkZone != null) {
                String backgroundId = shrinkZone.getBackgroundId();
                BackgroundModel background = backgroundService.loadBackground(backgroundId);
                if (background != null) {
                    // 构建径向边界网格用于O(1)侵入检测
                    float shrinkDistanceCm = shrinkZone.getShrinkDistanceCm() != null
                            ? shrinkZone.getShrinkDistanceCm()
                            : 20.0f;
                    background.buildBoundaryGrid(shrinkDistanceCm);

                    loadedBackgrounds.put(deviceId, background);
                    deviceStates.put(deviceId, "detecting");
                    logger.info("加载背景模型: deviceId={}, backgroundId={}, 点数={}, 边界网格有效方向={}",
                            deviceId, backgroundId, background.getPoints().size(),
                            background.getBoundaryGrid() != null ? background.getBoundaryGrid().getValidDirections()
                                    : 0);
                } else {
                    logger.warn("背景模型加载失败: deviceId={}, backgroundId={}", deviceId, backgroundId);
                }
            } else {
                logger.debug("没有启用的防区或背景ID为空: deviceId={}", deviceId);
            }
        }
    }

    /**
     * 设置设备状态
     */
    public void setDeviceState(String deviceId, String state) {
        deviceStates.put(deviceId, state);
    }

    // ==================== 降噪配置方法 ====================

    /**
     * 配置降噪参数
     * 
     * @param enabled         是否启用降噪
     * @param kNeighbors      K近邻数量（默认20）
     * @param stdDevThreshold 标准差阈值（默认1.0）
     */
    public void setDenoiseConfig(boolean enabled, int kNeighbors, float stdDevThreshold) {
        this.enableDenoising = enabled;
        this.denoiseKNeighbors = kNeighbors;
        this.denoiseStdDevThreshold = stdDevThreshold;
        logger.info("降噪配置已更新: enabled={}, kNeighbors={}, stdDevThreshold={}",
                enabled, kNeighbors, stdDevThreshold);
    }

    /**
     * 启用/禁用降噪
     */
    public void setEnableDenoising(boolean enabled) {
        this.enableDenoising = enabled;
        logger.info("降噪功能已{}", enabled ? "启用" : "禁用");
    }

    /**
     * 配置下采样参数
     * 
     * @param enabled    是否启用下采样
     * @param resolution 体素分辨率（米），例如0.05表示5cm
     */
    public void setVoxelDownsampleConfig(boolean enabled, float resolution) {
        this.enableVoxelDownsample = enabled;
        this.voxelResolution = resolution;
        logger.info("下采样配置已更新: enabled={}, resolution={}m", enabled, resolution);
    }

    /**
     * 获取当前降噪配置
     */
    public Map<String, Object> getDenoiseConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("enableDenoising", enableDenoising);
        config.put("denoiseKNeighbors", denoiseKNeighbors);
        config.put("denoiseStdDevThreshold", denoiseStdDevThreshold);
        config.put("enableVoxelDownsample", enableVoxelDownsample);
        config.put("voxelResolution", voxelResolution);
        return config;
    }
}
