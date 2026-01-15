package com.digital.video.gateway.service;

import com.digital.video.gateway.database.Database;
import com.digital.video.gateway.database.RadarBackground;
import com.digital.video.gateway.database.RadarBackgroundDAO;
import com.digital.video.gateway.database.RadarBackgroundPoint;
import com.digital.video.gateway.driver.livox.algorithm.PointCloudProcessor;
import com.digital.video.gateway.driver.livox.algorithm.SpatialIndex;
import com.digital.video.gateway.driver.livox.model.BackgroundModel;
import com.digital.video.gateway.driver.livox.model.BackgroundPoint;
import com.digital.video.gateway.driver.livox.model.Point;
import com.digital.video.gateway.driver.livox.model.PointCloudFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 背景建模服务
 * 负责采集、预处理、存储和加载背景点云模型
 */
public class BackgroundModelService {
    private static final Logger logger = LoggerFactory.getLogger(BackgroundModelService.class);

    private final Database database;
    private final Connection connection;
    private final RadarBackgroundDAO backgroundDAO;

    public RadarBackgroundDAO getBackgroundDAO() {
        return backgroundDAO;
    }

    // 采集状态管理
    private final Map<String, CollectionState> collectionStates = new ConcurrentHashMap<>();

    // 已加载的背景模型缓存
    private final Map<String, BackgroundModel> loadedModels = new ConcurrentHashMap<>();

    public BackgroundModelService(Database database) {
        this.database = database;
        this.connection = database.getConnection();
        this.backgroundDAO = new RadarBackgroundDAO(connection);

        // 启动时清理卡住的采集任务
        cleanupStuckCollections();
    }

    /**
     * 清理卡住的采集任务（数据库中状态为collecting但内存中没有对应状态的）
     */
    public void cleanupStuckCollections() {
        try {
            List<RadarBackground> allBackgrounds = backgroundDAO.getAll();
            int cleanedCount = 0;

            for (RadarBackground bg : allBackgrounds) {
                if ("collecting".equals(bg.getStatus())) {
                    // 检查是否超时（超过设定时长+60秒缓冲）
                    long elapsedMs = System.currentTimeMillis() - bg.getCreatedAt().getTime();
                    long maxDurationMs = (bg.getDurationSeconds() + 60) * 1000L;

                    if (elapsedMs > maxDurationMs || !collectionStates.containsKey(bg.getDeviceId())) {
                        // 标记为过期
                        bg.setStatus("expired");
                        backgroundDAO.update(bg);
                        cleanedCount++;
                        logger.warn("清理卡住的采集任务: backgroundId={}, 已运行{}秒",
                                bg.getBackgroundId(), elapsedMs / 1000);
                    }
                }
            }

            if (cleanedCount > 0) {
                logger.info("共清理 {} 个卡住的采集任务", cleanedCount);
            }
        } catch (Exception e) {
            logger.error("清理卡住的采集任务失败", e);
        }
    }

    /**
     * 开始采集背景
     * 
     * @param deviceId        设备ID
     * @param durationSeconds 采集时长（秒）
     * @param gridResolution  网格分辨率（米）
     * @return 背景ID
     */
    public String startCollection(String deviceId, int durationSeconds, float gridResolution) {
        String backgroundId = "bg_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);

        CollectionState state = new CollectionState();
        state.backgroundId = backgroundId;
        state.deviceId = deviceId;
        state.durationSeconds = durationSeconds;
        state.gridResolution = gridResolution;
        state.startTime = System.currentTimeMillis();
        state.frames = Collections.synchronizedList(new ArrayList<>());
        state.gridStats = new ConcurrentHashMap<>();

        // 创建定时任务执行器，用于自动结束采集
        state.scheduler = Executors.newSingleThreadScheduledExecutor();

        collectionStates.put(deviceId, state);

        // 创建背景记录
        RadarBackground background = new RadarBackground();
        background.setBackgroundId(backgroundId);
        background.setDeviceId(deviceId);
        background.setDurationSeconds(durationSeconds);
        background.setGridResolution(gridResolution);
        background.setStatus("collecting");
        background.setFrameCount(0);
        background.setPointCount(0);

        backgroundDAO.save(background);

        logger.info("开始采集背景: deviceId={}, backgroundId={}, duration={}s, resolution={}m",
                deviceId, backgroundId, durationSeconds, gridResolution);

        // 调度自动结束任务
        state.scheduler.schedule(() -> {
            logger.info("采集时间到达，自动停止采集: deviceId={}, backgroundId={}", deviceId, backgroundId);
            stopCollection(deviceId);
        }, durationSeconds, TimeUnit.SECONDS);

        return backgroundId;
    }

    /**
     * 添加点云帧到采集
     */
    public void addFrame(String deviceId, PointCloudFrame frame) {
        CollectionState state = collectionStates.get(deviceId);
        if (state == null) {
            return; // 未在采集状态
        }

        // 检查是否超时
        long elapsed = (System.currentTimeMillis() - state.startTime) / 1000;
        if (elapsed >= state.durationSeconds) {
            return; // 采集时间已到
        }

        state.frames.add(frame);
        state.frameCount.incrementAndGet();

        // 累积点到网格统计
        for (Point point : frame.getPoints()) {
            String gridKey = SpatialIndex.getGridKey(point, state.gridResolution);
            GridStatistics stats = state.gridStats.computeIfAbsent(gridKey, k -> new GridStatistics());
            stats.addPoint(point);
        }
    }

    /**
     * 停止采集并生成背景模型
     */
    public String stopCollection(String deviceId) {
        CollectionState state = collectionStates.remove(deviceId);
        if (state == null) {
            logger.warn("设备未在采集状态: {}", deviceId);
            return null;
        }

        // 关闭定时器
        if (state.scheduler != null) {
            state.scheduler.shutdownNow();
        }

        try {
            // 1. 汇总所有采集到的点
            List<Point> allPoints = new ArrayList<>();
            for (PointCloudFrame frame : state.frames) {
                allPoints.addAll(frame.getPoints());
            }
            logger.info("汇总原始点数: {}", allPoints.size());

            // 2. 预处理：体素下采样
            List<Point> downsampled = PointCloudProcessor.voxelDownsample(allPoints, state.gridResolution);
            logger.info("下采样后点数: {}", downsampled.size());

            // 3. 统计去噪（点数保护：超过1万点跳过，防止O(N^2)算法卡死）
            List<Point> denoisePoints = downsampled;
            if (downsampled.size() < 10000) {
                logger.info("执行统计去噪...");
                denoisePoints = PointCloudProcessor.statisticalOutlierRemoval(downsampled);
                logger.info("去噪后点数: {}", denoisePoints.size());
            } else {
                logger.warn("下采样点数过多 ({})，跳过统计去噪以防止阻塞", downsampled.size());
            }

            // 4. 保存点云到文件系统
            String filePath = BackgroundPointFileStorage.savePoints(state.backgroundId, denoisePoints);

            // 5. 更新数据库状态为 ready
            RadarBackground background = backgroundDAO.getByBackgroundId(state.backgroundId);
            if (background != null) {
                background.setFrameCount(state.frameCount.get());
                background.setPointCount(denoisePoints.size());
                background.setFilePath(filePath);
                background.setStatus("ready");
                backgroundDAO.save(background); // 使用 save 确保覆盖所有字段
                logger.info("背景模型保存成功: backgroundId={}, 状态=ready", state.backgroundId);
            }

            return state.backgroundId;
        } catch (Exception e) {
            logger.error("停止采集时发生严重错误: deviceId={}", deviceId, e);
            // 补偿逻辑：尝试将数据库状态设为失败
            try {
                RadarBackground background = backgroundDAO.getByBackgroundId(state.backgroundId);
                if (background != null) {
                    background.setStatus("error");
                    backgroundDAO.save(background);
                }
            } catch (Exception dbEx) {
                logger.error("补偿更新状态失败", dbEx);
            }
            return null;
        }
    }

    /**
     * 获取采集状态
     */
    public CollectionStatus getCollectionStatus(String deviceId) {
        CollectionState state = collectionStates.get(deviceId);
        if (state == null) {
            return null;
        }

        long elapsed = (System.currentTimeMillis() - state.startTime) / 1000;
        float progress = Math.min(1.0f, (float) elapsed / state.durationSeconds);
        int remainingSeconds = Math.max(0, state.durationSeconds - (int) elapsed);

        CollectionStatus status = new CollectionStatus();
        status.backgroundId = state.backgroundId;
        status.status = "collecting";
        status.progress = progress;
        status.frameCount = state.frameCount.get();
        status.estimatedRemainingSeconds = remainingSeconds;

        return status;
    }

    /**
     * 删除背景模型
     * 
     * @param backgroundId 背景ID
     * @return 是否成功删除
     */
    public boolean deleteBackground(String backgroundId) {
        try {
            // 1. 从缓存中移除
            loadedModels.remove(backgroundId);

            // 2. 删除文件
            BackgroundPointFileStorage.deleteFile(backgroundId);

            // 3. 从数据库删除（delete方法已经包含了删除关联点数据的逻辑）
            boolean deleted = backgroundDAO.delete(backgroundId);

            logger.info("背景模型删除完成: backgroundId={}, dbDeleted={}", backgroundId, deleted);
            return deleted;
        } catch (Exception e) {
            logger.error("删除背景模型失败: {}", backgroundId, e);
            return false;
        }
    }

    /**
     * 加载背景模型到内存
     */
    public BackgroundModel loadBackground(String backgroundId) {
        // 检查缓存
        if (loadedModels.containsKey(backgroundId)) {
            return loadedModels.get(backgroundId);
        }

        try {
            RadarBackground background = backgroundDAO.getByBackgroundId(backgroundId);
            if (background == null || !"ready".equals(background.getStatus())) {
                logger.warn("背景模型不存在或未就绪: {}", backgroundId);
                return null;
            }

            BackgroundModel model = new BackgroundModel();
            model.setBackgroundId(backgroundId);
            model.setDeviceId(background.getDeviceId());
            model.setGridResolution(background.getGridResolution());

            // 从文件加载背景点云（而非数据库）
            List<BackgroundPoint> points = new ArrayList<>();

            if (BackgroundPointFileStorage.fileExists(backgroundId)) {
                // 加载全部点云用于检测
                List<Point> filePoints = BackgroundPointFileStorage.loadPoints(backgroundId, 0);

                // 转换为 BackgroundPoint
                for (Point p : filePoints) {
                    BackgroundPoint bgPoint = new BackgroundPoint();
                    bgPoint.setCenterX(p.x);
                    bgPoint.setCenterY(p.y);
                    bgPoint.setCenterZ(p.z);
                    bgPoint.setMeanDistance((float) Math.sqrt(p.x * p.x + p.y * p.y + p.z * p.z));
                    points.add(bgPoint);
                }
                logger.info("从文件加载背景点云: backgroundId={}, 点数={}", backgroundId, points.size());
            } else {
                logger.warn("背景点云文件不存在: {}", backgroundId);
            }

            model.setPoints(points);
            loadedModels.put(backgroundId, model);

            logger.info("背景模型加载成功: backgroundId={}, points={}", backgroundId, points.size());
            return model;
        } catch (Exception e) {
            logger.error("加载背景模型失败: {}", backgroundId, e);
            return null;
        }
    }

    // 注意：背景点云数据已改为文件存储，不再保存到数据库
    // 此方法已废弃，保留用于兼容性
    @Deprecated
    private void saveBackgroundPoints(String backgroundId, List<BackgroundPoint> points) throws SQLException {
        // 不再保存到数据库，改为文件存储
        logger.warn("saveBackgroundPoints方法已废弃，背景点云数据应使用文件存储");
    }

    /**
     * 采集状态内部类
     */
    private static class CollectionState {
        String backgroundId;
        String deviceId;
        int durationSeconds;
        float gridResolution;
        long startTime;
        List<PointCloudFrame> frames;
        Map<String, GridStatistics> gridStats;
        AtomicInteger frameCount = new AtomicInteger(0);
        ScheduledExecutorService scheduler; // 自动结束定时器
    }

    /**
     * 网格统计内部类
     */
    private static class GridStatistics {
        int count = 0;
        float sumDistance = 0;
        List<Float> distances = new ArrayList<>();

        void addPoint(Point point) {
            count++;
            float distance = point.distance();
            sumDistance += distance;
            distances.add(distance);
        }

        float getMeanDistance() {
            return count > 0 ? sumDistance / count : 0;
        }

        float getStdDeviation() {
            if (count <= 1)
                return 0;
            float mean = getMeanDistance();
            float variance = 0;
            for (float dist : distances) {
                float diff = dist - mean;
                variance += diff * diff;
            }
            return (float) Math.sqrt(variance / count);
        }
    }

    /**
     * 获取采集中的点云数据（用于实时预览）
     * 注意：实时点云数据应该通过WebSocket推送，此方法仅用于采集过程中的预览
     */
    public List<com.digital.video.gateway.driver.livox.model.Point> getCollectingPointCloud(String deviceId,
            int maxPoints) {
        CollectionState state = collectionStates.get(deviceId);
        if (state == null || state.frames.isEmpty()) {
            return new ArrayList<>();
        }

        // 从最近的帧中提取点云（采样）
        List<com.digital.video.gateway.driver.livox.model.Point> allPoints = new ArrayList<>();
        int frameCount = Math.min(10, state.frames.size()); // 取最近10帧
        for (int i = state.frames.size() - frameCount; i < state.frames.size(); i++) {
            allPoints.addAll(state.frames.get(i).getPoints());
        }

        // 采样：如果点数太多，随机采样
        if (allPoints.size() > maxPoints) {
            List<com.digital.video.gateway.driver.livox.model.Point> sampled = new ArrayList<>();
            int step = allPoints.size() / maxPoints;
            for (int i = 0; i < allPoints.size(); i += step) {
                sampled.add(allPoints.get(i));
            }
            return sampled;
        }

        return allPoints;
    }

    /**
     * 从文件加载背景点云数据
     */
    public List<com.digital.video.gateway.driver.livox.model.Point> loadBackgroundPointsFromFile(String backgroundId,
            int maxPoints) {
        try {
            return BackgroundPointFileStorage.loadPoints(backgroundId, maxPoints);
        } catch (IOException e) {
            logger.error("从文件加载背景点云失败: {}", backgroundId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 采集状态DTO
     */
    public static class CollectionStatus {
        public String backgroundId;
        public String status;
        public float progress;
        public int frameCount;
        public int estimatedRemainingSeconds;
    }
}
