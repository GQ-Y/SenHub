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
    }

    /**
     * 开始采集背景
     * 
     * @param deviceId 设备ID
     * @param durationSeconds 采集时长（秒）
     * @param gridResolution 网格分辨率（米）
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

        try {
            // 1. 预处理：体素下采样和去噪
            List<Point> allPoints = new ArrayList<>();
            for (PointCloudFrame frame : state.frames) {
                allPoints.addAll(frame.getPoints());
            }

            logger.info("原始点数: {}", allPoints.size());

            // 体素下采样
            List<Point> downsampled = PointCloudProcessor.voxelDownsample(allPoints, state.gridResolution);
            logger.info("下采样后点数: {}", downsampled.size());

            // 统计去噪
            List<Point> denoised = PointCloudProcessor.statisticalOutlierRemoval(downsampled);
            logger.info("去噪后点数: {}", denoised.size());

            // 2. 生成背景点
            List<BackgroundPoint> backgroundPoints = new ArrayList<>();
            for (Point point : denoised) {
                String gridKey = SpatialIndex.getGridKey(point, state.gridResolution);
                GridStatistics stats = state.gridStats.get(gridKey);
                
                BackgroundPoint bgPoint = new BackgroundPoint();
                bgPoint.setGridKey(gridKey);
                bgPoint.setCenterX(point.x);
                bgPoint.setCenterY(point.y);
                bgPoint.setCenterZ(point.z);
                bgPoint.setPointCount(stats != null ? stats.count : 1);
                bgPoint.setMeanDistance(stats != null ? stats.getMeanDistance() : point.distance());
                bgPoint.setStdDeviation(stats != null ? stats.getStdDeviation() : 0);
                
                backgroundPoints.add(bgPoint);
            }

            // 3. 转换为Point列表（用于文件存储）
            List<Point> pointsForFile = new ArrayList<>();
            for (BackgroundPoint bgPoint : backgroundPoints) {
                Point point = new Point(
                    bgPoint.getCenterX(),
                    bgPoint.getCenterY(),
                    bgPoint.getCenterZ(),
                    (byte)0 // reflectivity
                );
                pointsForFile.add(point);
            }
            
            // 4. 保存到文件
            String filePath = BackgroundPointFileStorage.savePoints(state.backgroundId, pointsForFile);

            // 5. 更新背景记录（包含文件路径）
            RadarBackground background = backgroundDAO.getByBackgroundId(state.backgroundId);
            if (background != null) {
                background.setFrameCount(state.frameCount.get());
                background.setPointCount(backgroundPoints.size());
                background.setFilePath(filePath);
                background.setStatus("ready");
                backgroundDAO.update(background);
            }

            logger.info("背景采集完成: backgroundId={}, frames={}, points={}", 
                    state.backgroundId, state.frameCount.get(), backgroundPoints.size());

            return state.backgroundId;
        } catch (Exception e) {
            logger.error("停止采集失败: deviceId={}", deviceId, e);
            // 更新状态为失败
            RadarBackground background = backgroundDAO.getByBackgroundId(state.backgroundId);
            if (background != null) {
                background.setStatus("expired");
                backgroundDAO.update(background);
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

            // 从数据库加载背景点
            List<RadarBackgroundPoint> dbPoints = backgroundDAO.getPointsByBackgroundId(backgroundId);
            
            BackgroundModel model = new BackgroundModel();
            model.setBackgroundId(backgroundId);
            model.setDeviceId(background.getDeviceId());
            model.setGridResolution(background.getGridResolution());

            // 转换为BackgroundPoint
            List<BackgroundPoint> points = new ArrayList<>();
            
            for (RadarBackgroundPoint dbPoint : dbPoints) {
                BackgroundPoint bgPoint = new BackgroundPoint();
                bgPoint.setGridKey(dbPoint.getGridKey());
                bgPoint.setCenterX(dbPoint.getCenterX());
                bgPoint.setCenterY(dbPoint.getCenterY());
                bgPoint.setCenterZ(dbPoint.getCenterZ());
                bgPoint.setPointCount(dbPoint.getPointCount());
                bgPoint.setMeanDistance(dbPoint.getMeanDistance());
                bgPoint.setStdDeviation(dbPoint.getStdDeviation());
                
                points.add(bgPoint);
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
            if (count <= 1) return 0;
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
    public List<com.digital.video.gateway.driver.livox.model.Point> getCollectingPointCloud(String deviceId, int maxPoints) {
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
    public List<com.digital.video.gateway.driver.livox.model.Point> loadBackgroundPointsFromFile(String backgroundId, int maxPoints) {
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
