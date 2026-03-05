package com.digital.video.gateway.service;

import com.digital.video.gateway.driver.livox.LivoxDriver;
import com.digital.video.gateway.driver.livox.LivoxJNI;
import com.digital.video.gateway.driver.livox.protocol.SdkPacket;
import com.digital.video.gateway.driver.livox.model.*;
import com.digital.video.gateway.driver.livox.algorithm.CoordinateTransform;
import com.digital.video.gateway.driver.livox.algorithm.PointCloudProcessor;
import com.digital.video.gateway.database.Assembly;
import com.digital.video.gateway.database.Database;
import com.digital.video.gateway.database.RadarDevice;
import com.digital.video.gateway.database.RadarDeviceDAO;
import com.digital.video.gateway.api.RadarWebSocketHandler;
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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
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
    private final TargetTrackingService targetTrackingService; // 目标跟踪服务
    private final MotionPredictionService motionPredictionService; // 运动预测服务
    private CaptureService captureService;
    private AssemblyService assemblyService;
    private PointCloudProcessCenter pointCloudProcessCenter;

    // WebSocket处理器（用于实时推送）
    private RadarWebSocketHandler webSocketHandler;

    // 设备状态管理（deviceId -> 状态）
    private final Map<String, String> deviceStates = new ConcurrentHashMap<>(); // "collecting" 或 "detecting"
    private final Map<String, BackgroundModel> loadedBackgrounds = new ConcurrentHashMap<>(); // deviceId ->
                                                                                              // BackgroundModel
    private final Map<String, List<DefenseZone>> deviceZones = new ConcurrentHashMap<>(); // deviceId ->
                                                                                          // List<DefenseZone>
    private final Map<String, String> deviceSerialMap = new ConcurrentHashMap<>(); // deviceId -> radarSerial
    private final Map<String, Boolean> deviceConnectionStatus = new ConcurrentHashMap<>(); // deviceId -> connected
    
    // SDK 设备信息缓存：handle -> (serial, ip)
    private final Map<Integer, String[]> sdkDeviceInfoCache = new ConcurrentHashMap<>(); // handle -> [serial, ip]
    private final Map<String, Integer> ipToHandleMap = new ConcurrentHashMap<>(); // ip -> handle
    // handle -> deviceId 缓存，避免每帧回调重复查库（匹配成功后永久缓存）
    private final Map<Integer, String> handleToDeviceIdCache = new ConcurrentHashMap<>();
    
    // 点云数据超时检测（多雷达状态监测增强）
    private final Map<String, Long> lastPointCloudTime = new ConcurrentHashMap<>(); // deviceId -> 最后接收点云的时间戳
    private static final long POINTCLOUD_TIMEOUT_MS = 30000; // 30秒未收到点云数据则标记为离线
    private ScheduledExecutorService timeoutCheckExecutor;

    // 点云数据统计
    private final AtomicLong totalPointCloudFrames = new AtomicLong(0);
    private final AtomicLong totalPointCloudPoints = new AtomicLong(0);
    /** 因点云处理队列满而丢弃的帧数（用于日志定位瓶颈） */
    private final AtomicLong rejectedPointCloudFrames = new AtomicLong(0);
    private ScheduledExecutorService statsExecutor;
    private RadarStatusMonitor statusMonitor;
    private int statsLogIntervalSeconds = 60; // 点云统计日志间隔（秒）

    /** PTZ 联动抓拍：共用调度器，按设备防抖，避免同一球机短时间内多次抓拍导致设备锁等待超时 */
    private static final ScheduledExecutorService ptzCaptureScheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "PTZCaptureScheduler");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, ScheduledFuture<?>> pendingPtzCaptures = new ConcurrentHashMap<>();

    /** 侵入点数排查：按设备限流，避免刷屏 */
    private static final long INTRUSION_LOG_INTERVAL_MS = 5000;
    private final Map<String, Long> lastIntrusionLogTime = new ConcurrentHashMap<>();

    /**
     * 点云处理线程池：将 routePointCloud（降噪/侵入检测/推送）从 Livox 回调线程中剥离，
     * 避免在检测模式下阻塞回调导致 UDP 收包变慢、点云吞吐量骤降。
     * Mid-360 约 2,000+ 帧/秒，需 4 线程 + 256 深度队列才能保证检测模式下 < 5% 丢帧。
     */
    private static final int POINT_CLOUD_WORKER_THREADS = 4;
    private static final int POINT_CLOUD_QUEUE_CAPACITY = 256;
    private final AtomicLong lastQueueFullLogTime = new AtomicLong(0);
    private final ThreadPoolExecutor pointCloudExecutor = new ThreadPoolExecutor(
            POINT_CLOUD_WORKER_THREADS, POINT_CLOUD_WORKER_THREADS, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(POINT_CLOUD_QUEUE_CAPACITY),
            r -> {
                Thread t = new Thread(r, "PointCloudWorker");
                t.setDaemon(true);
                return t;
            },
            (r, e) -> {
                rejectedPointCloudFrames.incrementAndGet();
                long now = System.currentTimeMillis();
                long last = lastQueueFullLogTime.get();
                if (now - last >= 5000 && lastQueueFullLogTime.compareAndSet(last, now)) {
                    logger.warn("点云处理队列已满，丢弃帧（最近5秒丢弃: {}）", rejectedPointCloudFrames.get());
                }
            }
    );

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
        this.targetTrackingService = new TargetTrackingService();
        this.motionPredictionService = new MotionPredictionService();
        this.webSocketHandler = new RadarWebSocketHandler(this);
        this.statusMonitor = new RadarStatusMonitor(database);
    }

    /**
     * 设置抓拍服务（用于PTZ联动抓拍）
     */
    public void setCaptureService(CaptureService captureService) {
        this.captureService = captureService;
    }

    /**
     * 设置装置服务（用于读取装置 PTZ 联动开关）
     */
    public void setAssemblyService(AssemblyService assemblyService) {
        this.assemblyService = assemblyService;
    }

    /**
     * 设置点云处理中心（用于目标分类、跟踪、PTZ联动工作流）
     */
    public void setPointCloudProcessCenter(PointCloudProcessCenter pointCloudProcessCenter) {
        this.pointCloudProcessCenter = pointCloudProcessCenter;
    }

    public PointCloudProcessCenter getPointCloudProcessCenter() {
        return pointCloudProcessCenter;
    }

    public TargetTrackingService getTargetTrackingService() {
        return targetTrackingService;
    }

    public MotionPredictionService getMotionPredictionService() {
        return motionPredictionService;
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
            // 运行诊断检查
            com.digital.video.gateway.driver.livox.LivoxDiagnostics.runDiagnostics();
            
            livoxDriver.start();
            livoxDriver.setPointCloudCallback(this::handlePacket);
            
            // 注册设备信息变化回调（用于获取 SN 和检测设备连接/断开）
            registerDeviceInfoCallback();
            
            logger.info("雷达监听服务已启动 (JNI Driver)");

            // 启动统计信息打印任务（按配置间隔打印）
            startStatsReporter();

            // 服务启动时自动加载所有设备的检测上下文（恢复检测状态）
            loadAllDeviceDetectionContexts();
            if (statusMonitor != null) {
                statusMonitor.start();
            }
            
            // 启动点云数据超时检测任务（多雷达状态监测增强）
            startPointCloudTimeoutChecker();
        } catch (Exception e) {
            logger.error("启动雷达监听服务失败", e);
        }
    }
    
    /**
     * 注册设备信息变化回调
     * SDK 会在设备连接/断开时自动调用，提供设备的 SN 和 IP
     */
    private void registerDeviceInfoCallback() {
        try {
            LivoxJNI.setDeviceInfoCallback((handle, devType, serial, ip) -> {
                logger.info("SDK 设备信息回调: handle={}, devType={}, serial={}, ip={}", 
                        handle, devType, serial, ip);
                
                // 缓存设备信息
                if (serial != null && ip != null) {
                    sdkDeviceInfoCache.put(handle, new String[]{serial, ip});
                    ipToHandleMap.put(ip, handle);
                    
                    // 更新数据库中的设备状态为在线
                    updateDeviceStatusByIpOrSerial(ip, serial, true);
                }
            });
            logger.info("已注册 SDK 设备信息回调");
        } catch (UnsatisfiedLinkError e) {
            logger.warn("JNI 设备信息回调不可用（可能需要重新编译 JNI 库）: {}", e.getMessage());
        } catch (Exception e) {
            logger.warn("注册设备信息回调失败", e);
        }
    }
    
    /**
     * 根据 IP 或 SN 更新设备状态
     * 如果通过 SN 找到设备且 IP 发生变化，自动同步新 IP
     */
    private void updateDeviceStatusByIpOrSerial(String ip, String serial, boolean online) {
        RadarDeviceDAO dao = new RadarDeviceDAO(database.getConnection());
        
        // 优先通过序列号查找
        RadarDevice device = dao.getBySerial(serial);
        if (device != null) {
            String oldIp = device.getRadarIp();
            boolean ipChanged = oldIp != null && !oldIp.equals(ip);
            
            if (ipChanged) {
                // IP 发生变化，同时更新 IP 和状态
                dao.updateIpAndStatus(device.getDeviceId(), ip, online ? 1 : 0);
                logger.info("检测到雷达IP变化，已自动同步: deviceId={}, serial={}, oldIp={} -> newIp={}", 
                        device.getDeviceId(), serial, oldIp, ip);
            } else {
                // IP 未变化，只更新状态
                dao.updateStatus(device.getDeviceId(), online ? 1 : 0);
                logger.info("通过序列号更新设备状态: deviceId={}, serial={}, online={}", 
                        device.getDeviceId(), serial, online);
            }
            
            deviceConnectionStatus.put(device.getDeviceId(), online);
            // 更新内存中的序列号映射
            deviceSerialMap.put(device.getDeviceId(), serial);
            return;
        }
        
        // 通过 IP 查找（用于序列号为空的旧设备）
        List<RadarDevice> devices = dao.getAll();
        for (RadarDevice d : devices) {
            if (ip.equals(d.getRadarIp())) {
                // 如果设备没有序列号，自动补充序列号
                if (d.getRadarSerial() == null || d.getRadarSerial().isEmpty()) {
                    d.setRadarSerial(serial);
                    dao.saveOrUpdate(d);
                    logger.info("为已有设备补充序列号: deviceId={}, ip={}, serial={}", 
                            d.getDeviceId(), ip, serial);
                }
                dao.updateStatus(d.getDeviceId(), online ? 1 : 0);
                deviceConnectionStatus.put(d.getDeviceId(), online);
                deviceSerialMap.put(d.getDeviceId(), serial);
                logger.info("通过IP更新设备状态: deviceId={}, ip={}, online={}", 
                        d.getDeviceId(), ip, online);
                return;
            }
        }
        
        logger.debug("未找到匹配的设备: ip={}, serial={}", ip, serial);
    }
    
    /**
     * 根据 IP 获取 SDK 检测到的设备序列号
     * 用于 RadarTestService 进行设备检测
     * @param ip 设备 IP 地址
     * @return 设备序列号，如果未检测到返回 null
     */
    public String getDeviceSerialByIp(String ip) {
        // 先从 JNI 缓存查询
        try {
            String serial = LivoxJNI.getDeviceSerialByIp(ip);
            if (serial != null && !serial.isEmpty()) {
                logger.debug("从 JNI 缓存获取到设备序列号: ip={}, serial={}", ip, serial);
                return serial;
            }
        } catch (UnsatisfiedLinkError e) {
            logger.debug("JNI 查询不可用: {}", e.getMessage());
        }
        
        // 从内存缓存查询
        Integer handle = ipToHandleMap.get(ip);
        if (handle != null) {
            String[] info = sdkDeviceInfoCache.get(handle);
            if (info != null && info.length > 0) {
                logger.debug("从内存缓存获取到设备序列号: ip={}, serial={}", ip, info[0]);
                return info[0];
            }
        }
        
        return null;
    }
    
    /**
     * 检查指定 IP 的设备是否已被 SDK 检测到
     * @param ip 设备 IP 地址
     * @return 如果 SDK 已检测到该设备返回 true
     */
    public boolean isDeviceDetectedBySDK(String ip) {
        return getDeviceSerialByIp(ip) != null;
    }
    
    /**
     * 获取所有 SDK 检测到的设备信息
     * @return Map: IP -> Serial
     */
    public Map<String, String> getAllDetectedDevices() {
        Map<String, String> devices = new HashMap<>();
        
        // 从 JNI 获取所有连接的设备
        try {
            int[] handles = LivoxJNI.getConnectedDeviceHandles();
            if (handles != null) {
                for (int handle : handles) {
                    String serial = LivoxJNI.getDeviceSerial(handle);
                    String ip = LivoxJNI.getDeviceIp(handle);
                    if (serial != null && ip != null) {
                        devices.put(ip, serial);
                    }
                }
            }
        } catch (UnsatisfiedLinkError e) {
            logger.debug("JNI 查询不可用，使用内存缓存: {}", e.getMessage());
        }
        
        // 补充内存缓存中的数据
        for (Map.Entry<Integer, String[]> entry : sdkDeviceInfoCache.entrySet()) {
            String[] info = entry.getValue();
            if (info != null && info.length >= 2) {
                devices.putIfAbsent(info[1], info[0]); // ip -> serial
            }
        }
        
        return devices;
    }

    /**
     * 启动统计信息报告器（按配置的间隔打印）
     */
    private void startStatsReporter() {
        statsExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "RadarService-StatsReporter");
            t.setDaemon(true);
            return t;
        });

        if (statsLogIntervalSeconds <= 0) {
            logger.info("点云统计报告器未启动：日志间隔配置为{}", statsLogIntervalSeconds);
            return;
        }

        final AtomicLong lastTotalFrames = new AtomicLong(0);
        final AtomicLong lastTotalPoints = new AtomicLong(0);
        final AtomicLong lastRejectedFrames = new AtomicLong(0);

        statsExecutor.scheduleAtFixedRate(() -> {
            long currentFrames = totalPointCloudFrames.get();
            long currentPoints = totalPointCloudPoints.get();
            long currentRejected = rejectedPointCloudFrames.get();

            long framesThisPeriod = currentFrames - lastTotalFrames.get();
            long pointsThisPeriod = currentPoints - lastTotalPoints.get();
            long rejectedThisPeriod = currentRejected - lastRejectedFrames.get();

            lastTotalFrames.set(currentFrames);
            lastTotalPoints.set(currentPoints);
            lastRejectedFrames.set(currentRejected);

            int queueSize = pointCloudExecutor.getQueue().size();
            int activeCount = pointCloudExecutor.getActiveCount();

            logger.info("[点云统计] 本周期接收: {} 帧, {} 点 | 本周期丢弃: {} 帧 | 累计接收: {} 帧, {} 点, 累计丢弃: {} 帧 | 处理队列: 排队={}, 工作中={}",
                    framesThisPeriod, pointsThisPeriod, rejectedThisPeriod,
                    currentFrames, currentPoints, currentRejected,
                    queueSize, activeCount);
        }, statsLogIntervalSeconds, statsLogIntervalSeconds, TimeUnit.SECONDS);

        logger.info("点云统计报告器已启动（每{}秒打印一次）", statsLogIntervalSeconds);
    }

    /**
     * 启动点云数据超时检测任务
     * 定期检查每个设备是否在指定时间内收到点云数据
     * 如果超时则标记设备为离线
     */
    private void startPointCloudTimeoutChecker() {
        timeoutCheckExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "RadarService-TimeoutChecker");
            t.setDaemon(true);
            return t;
        });

        // 每10秒检查一次超时
        timeoutCheckExecutor.scheduleAtFixedRate(() -> {
            try {
                checkPointCloudTimeout();
            } catch (Exception e) {
                logger.error("点云超时检测任务异常", e);
            }
        }, 10, 10, TimeUnit.SECONDS);

        logger.info("点云数据超时检测已启动（超时阈值={}ms）", POINTCLOUD_TIMEOUT_MS);
    }

    /**
     * 检查点云数据超时
     * 遍历所有在线设备，检查是否超时未收到点云数据
     */
    private void checkPointCloudTimeout() {
        long now = System.currentTimeMillis();
        
        for (Map.Entry<String, Boolean> entry : deviceConnectionStatus.entrySet()) {
            String deviceId = entry.getKey();
            Boolean connected = entry.getValue();
            
            if (Boolean.TRUE.equals(connected)) {
                Long lastTime = lastPointCloudTime.get(deviceId);
                if (lastTime != null && (now - lastTime) > POINTCLOUD_TIMEOUT_MS) {
                    // 超时，标记为离线
                    logger.warn("设备 {} 点云数据超时（最后接收: {}ms前），标记为离线", 
                            deviceId, (now - lastTime));
                    syncDeviceStatus(deviceId, false);
                    // 清除超时记录，避免重复触发
                    lastPointCloudTime.remove(deviceId);
                }
            }
        }
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
        
        // 停止点云超时检测任务
        if (timeoutCheckExecutor != null) {
            timeoutCheckExecutor.shutdown();
            try {
                if (!timeoutCheckExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    timeoutCheckExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                timeoutCheckExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // 关闭 WebSocket 点云发送线程池
        if (webSocketHandler != null) {
            webSocketHandler.shutdown();
        }

        // 关闭点云处理线程池
        pointCloudExecutor.shutdown();
        try {
            if (!pointCloudExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                pointCloudExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            pointCloudExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // 关闭静态 PTZ 抓拍调度器
        ptzCaptureScheduler.shutdown();
        try {
            if (!ptzCaptureScheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                ptzCaptureScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            ptzCaptureScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        livoxDriver.stop();
        logger.info("雷达监听服务已停止");
        if (statusMonitor != null) {
            statusMonitor.stop();
        }
    }

    /**
     * 解析 Livox 数据包
     * 支持多雷达场景：通过 packet.handle 区分不同设备
     */
    private void handlePacket(SdkPacket packet) {
        if (packet.payload == null || packet.payload.length < 1)
            return;

        // 根据 handle 获取设备ID（多雷达支持的核心）
        String deviceId = null;
        if (packet.handle > 0) {
            deviceId = getDeviceIdByHandle(packet.handle);
        }
        
        // 如果通过 handle 找不到，回退到旧逻辑（兼容单雷达场景）
        if (deviceId == null) {
            deviceId = getCurrentDeviceId();
            if (deviceId != null && packet.handle > 0) {
                logger.debug("通过 handle={} 无法找到设备，使用默认设备: {}", packet.handle, deviceId);
            }
        }
        
        if (deviceId == null) {
            logger.debug("没有配置雷达设备，忽略点云数据");
            return;
        }

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

                // 提交到独立线程池处理，避免在 Livox 回调中执行侵入检测/推送导致阻塞、点云吞吐骤降
                submitPointCloud(deviceId, points);
            }
        } else if (dataType == 0x02) {
            // 低精度笛卡尔坐标
            List<Point> points = parseCartesianLowPoints(packet.payload);

            if (!points.isEmpty()) {
                totalPointCloudFrames.incrementAndGet();
                totalPointCloudPoints.addAndGet(points.size());

                submitPointCloud(deviceId, points);
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
            bb.get(); // 1 byte (忽略 tag)

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
            bb.get(); // 1 byte (忽略 tag)

            // cm -> m
            float x = xShort / 100.0f;
            float y = yShort / 100.0f;
            float z = zShort / 100.0f;

            points.add(new Point(x, y, z, reflectivity));
        }

        return points;
    }

    /**
     * 将点云提交到独立线程池处理，避免 Livox 回调阻塞。
     * 队列满时丢弃本帧（由 RejectedExecutionHandler 处理）。
     */
    private void submitPointCloud(String deviceId, List<Point> points) {
        pointCloudExecutor.execute(() -> {
            try {
                routePointCloud(deviceId, points);
            } catch (Exception e) {
                logger.warn("点云处理异常: deviceId={}", deviceId, e);
            }
        });
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

        // 更新最后接收点云的时间（用于超时检测）
        lastPointCloudTime.put(deviceId, System.currentTimeMillis());

        // 收到数据即视为在线，进行状态同步
        if (!Boolean.TRUE.equals(deviceConnectionStatus.get(deviceId))) {
            syncDeviceStatus(deviceId, true);
        }

        String state = deviceStates.get(deviceId);

        // 降噪处理（可选）：仅在检测模式下执行，以支撑 20 万点/秒实时预览
        // SOR 为 O(n²)，在 collecting/默认模式下跳过可避免队列满、漏点
        List<Point> processedPoints = points;
        if ("detecting".equals(state) && enableDenoising && points.size() > denoiseKNeighbors) {
            try {
                processedPoints = PointCloudProcessor.statisticalOutlierRemoval(
                        processedPoints,
                        denoiseKNeighbors,
                        denoiseStdDevThreshold);
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
                int intruderCount = 0;
                for (Point p : processedPoints) {
                    if (p.zoneId != null) intruderCount++;
                }
                if (intruderCount > 0) {
                    // 限流打点（约每5秒一次）
                    long now = System.currentTimeMillis();
                    if (now - lastIntrusionLogTime.getOrDefault(deviceId, 0L) >= INTRUSION_LOG_INTERVAL_MS) {
                        lastIntrusionLogTime.put(deviceId, now);
                        logger.info("侵入点数统计: deviceId={}, 本帧总点数={}, 本帧侵入点数={}", deviceId, beforeCount, intruderCount);
                    }
                }
            } else {
                if (background == null) {
                    logger.debug("检测模式但背景模型未加载: deviceId={}", deviceId);
                }
                if (zones == null || zones.isEmpty()) {
                    logger.debug("检测模式但防区未配置: deviceId={}", deviceId);
                }
            }

            // 2. 立即推送已标记的点云（不等待 DBSCAN，零延迟送达前端）
            if (webSocketHandler != null) {
                webSocketHandler.pushPointCloud(deviceId, processedPoints);
                webSocketHandler.logPushStats(deviceId);
            }

            // 3. 处理录制（缓冲和保存侵入片段）
            recordingManager.processFrame(deviceId, processedPoints);

            // 4. 生成侵入事件（用于报警和PTZ，不影响前端显示延迟）
            List<IntrusionEvent> events = processDetection(deviceId, processedPoints);
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

            // null = 节流中，跳过本帧处理（保持跟随状态不变）
            if (events == null) {
                if (pointCloudProcessCenter != null) {
                    pointCloudProcessCenter.processEvents(deviceId, null, zone);
                }
                continue;
            }

            allEvents.addAll(events);

            // 委托给点云处理中心（分类 → 跟踪 → PTZ解算 → 跟随状态机 → 触发工作流）
            if (pointCloudProcessCenter != null) {
                pointCloudProcessCenter.processEvents(deviceId, events, zone);
            } else {
                // 降级：无处理中心时走旧的直接处理逻辑
                for (IntrusionEvent event : events) {
                    PointCluster cluster = event.getCluster();
                    if (cluster != null && cluster.getCentroid() != null) {
                        String targetId = event.getClusterId();
                        targetTrackingService.updateTarget(targetId, cluster.getCentroid());
                    }
                }
                for (IntrusionEvent event : events) {
                    handleIntrusionEvent(deviceId, event, zone);
                }
            }
        }

        return allEvents;
    }

    /**
     * 处理侵入事件：PTZ联动和抓拍
     * 仅当以下条件同时满足时才执行 PTZ：
     * 1. 雷达已关联到装置（radar_devices.assembly_id 非空）
     * 2. 防区已配置关联球机（cameraDeviceId 非空）
     */
    private void handleIntrusionEvent(String radarDeviceId, IntrusionEvent event, DefenseZone zone) {
        // 1. 防区未配置球机则不进行 PTZ 联动
        String cameraDeviceId = zone.getCameraDeviceId();
        if (cameraDeviceId == null || cameraDeviceId.trim().isEmpty()) {
            return;
        }

        // 2. 雷达未关联装置则不进行 PTZ 联动
        RadarDeviceDAO radarDeviceDAO = new RadarDeviceDAO(database.getConnection());
        RadarDevice radar = radarDeviceDAO.getByDeviceId(radarDeviceId);
        if (radar == null || radar.getAssemblyId() == null || radar.getAssemblyId().trim().isEmpty()) {
            return;
        }

        // 3. 装置未开启 PTZ 联动则不执行
        if (assemblyService != null) {
            Assembly assembly = assemblyService.getAssembly(radar.getAssemblyId());
            if (assembly == null || !assembly.isPtzLinkageEnabled()) {
                if (logger.isDebugEnabled()) {
                    logger.debug("装置未开启PTZ联动，跳过: assemblyId={}, zoneId={}", radar.getAssemblyId(), zone.getZoneId());
                }
                return;
            }
        }

        PointCluster cluster = event.getCluster();
        if (cluster == null || cluster.getCentroid() == null) {
            return;
        }

        String targetId = event.getClusterId();
        Point currentRadarPoint = cluster.getCentroid();

        // 获取目标速度
        float[] velocity = targetTrackingService.getLatestVelocity(targetId);

        // 坐标系转换
        DefenseZone.CoordinateTransform transform = zone.getCoordinateTransform();
        CoordinateTransform coordTransform = new CoordinateTransform(
                transform.translationX, transform.translationY, transform.translationZ,
                transform.rotationX, transform.rotationY, transform.rotationZ,
                transform.scale);

        // PTZ延迟时间（秒）- 球机转向和变焦的延迟
        float ptzDelaySeconds = 0.5f; // 默认0.5秒，可根据实际情况调整

        // 运动预测：预测PTZ到位时的目标位置
        MotionPredictionService.PredictionResult prediction = motionPredictionService.predictForPTZ(
                currentRadarPoint, velocity, ptzDelaySeconds);

        // 将预测位置转换到摄像头坐标系
        Point predictedCameraPoint = coordTransform.transformRadarToCamera(prediction.predictedPosition);

        // 计算PTZ角度
        float[] angles = coordTransform.calculatePTZAngles(predictedCameraPoint);
        float pan = angles[0];
        float tilt = angles[1];

        // 根据目标状态计算变倍（zoom）
        float zoom = calculateZoom(prediction, predictedCameraPoint);

        // 驱动PTZ转向到预测位置
        int channel = zone.getCameraChannel() != null ? zone.getCameraChannel() : 1;
        boolean ptzSuccess = ptzService.gotoAngle(cameraDeviceId, channel, pan, tilt, zoom);

        if (ptzSuccess) {
            logger.info("侵入检测触发PTZ联动: zoneId={}, camera={}, targetId={}, " +
                    "state={}, pan={}°, tilt={}°, zoom={}x, confidence={}",
                    zone.getZoneId(), cameraDeviceId, targetId,
                    prediction.motionState, pan, tilt, zoom, String.format("%.2f", prediction.confidence));

            // 根据运动状态决定抓拍策略
            scheduleCapture(cameraDeviceId, channel, prediction, ptzDelaySeconds);
        } else {
            logger.warn("PTZ控制失败: zoneId={}, camera={}, targetId={}",
                    zone.getZoneId(), cameraDeviceId, targetId);
        }
    }

    /**
     * 根据目标状态和距离计算变倍（zoom）
     */
    private float calculateZoom(MotionPredictionService.PredictionResult prediction, Point cameraPoint) {
        // 计算目标距离
        float distance = (float) Math.sqrt(
                cameraPoint.x * cameraPoint.x +
                cameraPoint.y * cameraPoint.y +
                cameraPoint.z * cameraPoint.z);

        // 根据距离和状态调整变倍
        float baseZoom = 1.0f;
        
        if (distance > 50) {
            // 距离较远，放大
            baseZoom = 3.0f;
        } else if (distance > 20) {
            baseZoom = 2.0f;
        } else if (distance > 10) {
            baseZoom = 1.5f;
        }

        // 如果目标在下落，稍微放大以便抓拍
        if (prediction.motionState == MotionPredictionService.MotionState.FALLING) {
            baseZoom *= 1.2f;
        }

        // 限制变倍范围（1.0x - 10.0x）
        return Math.max(1.0f, Math.min(10.0f, baseZoom));
    }

    /**
     * 根据目标状态安排抓拍
     */
    private void scheduleCapture(String cameraDeviceId, int channel, 
                                 MotionPredictionService.PredictionResult prediction,
                                 float ptzDelaySeconds) {
        if (captureService == null) {
            logger.debug("抓拍服务未配置，跳过抓拍");
            return;
        }

        // 根据运动状态决定抓拍时机
        long captureDelayMs = 0;

        switch (prediction.motionState) {
            case FALLING:
                // 下落中：如果落地时间小于PTZ延迟，等待落地后抓拍
                if (prediction.landingPoint != null && prediction.timeToLand > 0) {
                    if (prediction.timeToLand <= ptzDelaySeconds) {
                        // 落地时间小于PTZ延迟，等待落地后抓拍
                        captureDelayMs = (long) ((prediction.timeToLand + 0.2f) * 1000); // 落地后0.2秒抓拍
                        logger.info("目标下落中，将在落地后抓拍: 落地时间={}秒, 抓拍延迟={}ms",
                                prediction.timeToLand, captureDelayMs);
                    } else {
                        // 落地时间大于PTZ延迟，PTZ到位后立即抓拍
                        captureDelayMs = (long) (ptzDelaySeconds * 1000);
                        logger.info("目标下落中，PTZ到位后抓拍: 延迟={}ms", captureDelayMs);
                    }
                } else {
                    // 无法预测落地时间，PTZ到位后立即抓拍
                    captureDelayMs = (long) (ptzDelaySeconds * 1000);
                }
                break;

            case HOVERING:
                // 滞空：PTZ到位后稍等片刻再抓拍，确保目标稳定
                captureDelayMs = (long) ((ptzDelaySeconds + 0.3f) * 1000);
                logger.info("目标滞空，PTZ到位后稳定抓拍: 延迟={}ms", captureDelayMs);
                break;

            case LANDED:
                // 已落地：立即抓拍
                captureDelayMs = (long) (ptzDelaySeconds * 1000);
                logger.info("目标已落地，PTZ到位后立即抓拍: 延迟={}ms", captureDelayMs);
                break;

            case MOVING:
            default:
                // 正常移动：PTZ到位后立即抓拍
                captureDelayMs = (long) (ptzDelaySeconds * 1000);
                logger.info("目标正常移动，PTZ到位后抓拍: 延迟={}ms", captureDelayMs);
                break;
        }

        // 安排异步抓拍任务（按设备防抖：同一球机只保留最后一次调度，避免并发抢锁超时）
        final String finalDeviceId = cameraDeviceId;
        final int finalChannel = channel;
        final long finalDelay = captureDelayMs;
        final MotionPredictionService.MotionState finalState = prediction.motionState;

        ScheduledFuture<?> existing = pendingPtzCaptures.remove(finalDeviceId);
        if (existing != null) {
            existing.cancel(false);
        }

        ScheduledFuture<?> future = ptzCaptureScheduler.schedule(() -> {
            try {
                String picPath = captureService.captureSnapshot(finalDeviceId, finalChannel);
                if (picPath != null) {
                    logger.info("PTZ联动抓拍成功: deviceId={}, channel={}, state={}, path={}",
                            finalDeviceId, finalChannel, finalState, picPath);
                } else {
                    logger.warn("PTZ联动抓拍失败: deviceId={}, channel={}", finalDeviceId, finalChannel);
                }
            } catch (Exception e) {
                logger.error("PTZ联动抓拍异常: deviceId={}, channel={}", finalDeviceId, finalChannel, e);
            } finally {
                pendingPtzCaptures.remove(finalDeviceId);
            }
        }, finalDelay, TimeUnit.MILLISECONDS);

        pendingPtzCaptures.put(finalDeviceId, future);
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
            com.digital.video.gateway.database.RadarDevice device = devices.get(0);
            cacheSerial(device);
            return device.getDeviceId();
        } else {
            // 多个雷达设备，优先使用配置指定的，否则使用第一个
            String configuredDeviceId = database.getConfig("radar.current_device_id");
            if (configuredDeviceId != null && !configuredDeviceId.trim().isEmpty()) {
                // 检查配置的设备ID是否存在
                for (com.digital.video.gateway.database.RadarDevice device : devices) {
                    if (configuredDeviceId.equals(device.getDeviceId())) {
                        cacheSerial(device);
                        return device.getDeviceId();
                    }
                }
            }
            // 使用第一个设备
            com.digital.video.gateway.database.RadarDevice first = devices.get(0);
            cacheSerial(first);
            logger.debug("多个雷达设备，使用第一个: {}", first.getDeviceId());
            return first.getDeviceId();
        }
    }

    /**
     * 根据 handle 获取设备ID（带缓存，避免每帧重复查库）。
     * 匹配成功后结果永久缓存在 handleToDeviceIdCache 中。
     */
    private String getDeviceIdByHandle(int handle) {
        // 1. 先查缓存，命中直接返回
        String cached = handleToDeviceIdCache.get(handle);
        if (cached != null) {
            return cached;
        }

        // 2. 从 SDK 设备信息缓存获取 serial 和 ip
        String[] info = sdkDeviceInfoCache.get(handle);
        if (info == null || info.length < 2) {
            logger.debug("无法找到 handle={} 对应的设备信息，缓存中无此 handle", handle);
            return null;
        }
        String serial = info[0];
        String ip = info[1];

        // 3. 优先通过 serial 查找 deviceId
        RadarDeviceDAO dao = new RadarDeviceDAO(database.getConnection());
        RadarDevice device = dao.getBySerial(serial);
        if (device != null) {
            // SN 匹配成功：若 IP 与数据库记录不一致，自动修正
            if (ip != null && !ip.isEmpty() && !ip.equals(device.getRadarIp())) {
                try {
                    dao.updateIpAndStatus(device.getDeviceId(), ip, 1);
                    logger.info("SN 匹配，自动修正雷达 IP: deviceId={}, serial={}, oldIp={} -> newIp={}",
                            device.getDeviceId(), serial, device.getRadarIp(), ip);
                } catch (Exception e) {
                    logger.warn("自动修正雷达 IP 失败: deviceId={}", device.getDeviceId(), e);
                }
            } else {
                logger.info("通过 serial 找到设备: handle={}, serial={}, deviceId={}", handle, serial, device.getDeviceId());
            }
            handleToDeviceIdCache.put(handle, device.getDeviceId());
            return device.getDeviceId();
        }

        // 4. SN 未配置或不匹配时，通过 IP 查找（兼容未填写 SN 的旧设备）
        List<RadarDevice> devices = dao.getAll();
        for (RadarDevice d : devices) {
            if (ip.equals(d.getRadarIp())) {
                logger.info("通过 IP 找到设备: handle={}, ip={}, deviceId={}", handle, ip, d.getDeviceId());
                // IP 匹配成功，若数据库 SN 为空则顺手写入，方便下次直接通过 SN 匹配
                if (serial != null && !serial.isEmpty() && (d.getRadarSerial() == null || d.getRadarSerial().isEmpty())) {
                    try {
                        dao.updateSerial(d.getDeviceId(), serial);
                        logger.info("已自动补全雷达 SN: deviceId={}, serial={}", d.getDeviceId(), serial);
                    } catch (Exception e) {
                        logger.warn("自动补全雷达 SN 失败: deviceId={}", d.getDeviceId(), e);
                    }
                }
                handleToDeviceIdCache.put(handle, d.getDeviceId());
                return d.getDeviceId();
            }
        }

        logger.warn("无法找到 handle={} (serial={}, ip={}) 对应的 deviceId，请检查雷达设备配置中的 SN 是否与实际设备一致",
                handle, serial, ip);
        return null;
    }

    /**
     * 缓存设备序列号映射，便于状态同步
     */
    private void cacheSerial(com.digital.video.gateway.database.RadarDevice device) {
        if (device != null && device.getRadarSerial() != null) {
            deviceSerialMap.put(device.getDeviceId(), device.getRadarSerial());
        }
    }

    /**
     * 同步设备在线状态到数据库
     * 增强：添加日志记录和状态变化检测
     */
    public void syncDeviceStatus(String deviceId, boolean connected) {
        Boolean previousStatus = deviceConnectionStatus.get(deviceId);
        boolean statusChanged = previousStatus == null || previousStatus != connected;
        
        deviceConnectionStatus.put(deviceId, connected);
        
        try {
            RadarDeviceDAO dao = new RadarDeviceDAO(database.getConnection());
            dao.updateStatus(deviceId, connected ? 1 : 0);
            
            if (statusChanged) {
                logger.info("设备状态变化: deviceId={}, {} -> {}", 
                        deviceId, 
                        previousStatus == null ? "未知" : (previousStatus ? "在线" : "离线"),
                        connected ? "在线" : "离线");
            }
        } catch (Exception e) {
            logger.error("同步设备状态到数据库失败: deviceId={}, connected={}", deviceId, connected, e);
        }
        
        // 设备离线时清理超时记录
        if (!connected) {
            lastPointCloudTime.remove(deviceId);
        }
    }
    
    /**
     * 获取所有设备的连接状态（供API使用）
     * @return Map: deviceId -> connected
     */
    public Map<String, Boolean> getAllDeviceConnectionStatus() {
        return new HashMap<>(deviceConnectionStatus);
    }
    
    /**
     * 获取所有设备的最后点云接收时间（供API使用）
     * @return Map: deviceId -> lastPointCloudTime (timestamp)
     */
    public Map<String, Long> getLastPointCloudTimeMap() {
        return new HashMap<>(lastPointCloudTime);
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

            int enabledCount = 0;
            for (com.digital.video.gateway.database.RadarDevice device : devices) {
                String deviceId = device.getDeviceId();
                try {
                    logger.info("服务启动: 加载设备检测上下文 deviceId={}", deviceId);
                    reloadDeviceDetectionContext(deviceId);
                    // 启动时自动恢复检测模式：背景模型和防区都已就绪则直接进入检测状态
                    BackgroundModel bg = loadedBackgrounds.get(deviceId);
                    List<DefenseZone> zones = deviceZones.get(deviceId);
                    if (bg != null && bg.getBoundaryGrid() != null && zones != null && !zones.isEmpty()) {
                        deviceStates.put(deviceId, "detecting");
                        enabledCount++;
                        logger.info("服务启动: 自动开启检测 deviceId={}", deviceId);
                    }
                } catch (Exception e) {
                    logger.error("服务启动: 加载设备检测上下文失败 deviceId={}", deviceId, e);
                }
            }

            logger.info("服务启动: 检测上下文加载完成，已加载 {} 个设备，自动开启检测 {} 个", devices.size(), enabledCount);
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
     * 重新加载设备的检测上下文（背景模型和防区配置）。
     * 如果之前已处于检测模式且重新加载后条件仍满足，自动保持检测状态。
     */
    public void reloadDeviceDetectionContext(String deviceId) {
        String previousState = deviceStates.getOrDefault(deviceId, "");

        // 1. 清除侵入检测服务的空间索引缓存，强制下次重建
        intrusionDetectionService.clearSpatialIndexCache();

        // 2. 重新加载防区配置
        loadZonesForDevice(deviceId);

        // 3. 查找防区中使用的背景并加载
        List<DefenseZone> zones = deviceZones.get(deviceId);
        if (zones != null && !zones.isEmpty()) {
            DefenseZone shrinkZone = zones.stream()
                    .filter(DefenseZone::getEnabled)
                    .filter(z -> z.getBackgroundId() != null && !z.getBackgroundId().isEmpty())
                    .findFirst()
                    .orElse(null);

            if (shrinkZone != null) {
                String backgroundId = shrinkZone.getBackgroundId();
                BackgroundModel background = backgroundService.loadBackground(backgroundId);
                if (background != null) {
                    float shrinkDistanceCm = shrinkZone.getShrinkDistanceCm() != null
                            ? shrinkZone.getShrinkDistanceCm()
                            : 20.0f;
                    background.buildBoundaryGrid(shrinkDistanceCm);

                    loadedBackgrounds.put(deviceId, background);
                    // 条件满足，自动恢复/保持检测状态
                    if ("detecting".equals(previousState)) {
                        deviceStates.put(deviceId, "detecting");
                        logger.info("重新加载并恢复检测模式: deviceId={}, backgroundId={}, 点数={}, 边界网格有效方向={}",
                                deviceId, backgroundId, background.getPoints().size(),
                                background.getBoundaryGrid() != null ? background.getBoundaryGrid().getValidDirections() : 0);
                    } else {
                        logger.info("加载背景模型: deviceId={}, backgroundId={}, 点数={}, 边界网格有效方向={}",
                                deviceId, backgroundId, background.getPoints().size(),
                                background.getBoundaryGrid() != null ? background.getBoundaryGrid().getValidDirections() : 0);
                    }
                } else {
                    logger.warn("背景模型加载失败: deviceId={}, backgroundId={}", deviceId, backgroundId);
                }
            } else {
                logger.debug("没有启用的防区或背景ID为空: deviceId={}", deviceId);
                deviceStates.put(deviceId, "");
            }
        } else {
            deviceStates.put(deviceId, "");
        }
    }

    /**
     * 获取设备当前状态（"collecting" | "detecting" | "" 等）
     */
    public String getDeviceState(String deviceId) {
        String s = deviceStates.get(deviceId);
        return s != null ? s : "";
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

    /**
     * 设置点云统计日志的打印间隔（秒）
     */
    public void setStatsLogIntervalSeconds(int statsLogIntervalSeconds) {
        if (statsLogIntervalSeconds > 0) {
            this.statsLogIntervalSeconds = statsLogIntervalSeconds;
        }
    }
}
