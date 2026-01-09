package com.digital.video.gateway.service;

import com.digital.video.gateway.database.Database;
import com.digital.video.gateway.database.DeviceInfo;
import com.digital.video.gateway.database.RecordingTask;
import com.digital.video.gateway.device.DeviceManager;
import com.digital.video.gateway.device.DeviceSDK;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * 录像任务服务
 * 实现异步录像下载、状态监控及 OSS 上传逻辑
 */
public class RecordingTaskService {
    private static final Logger logger = LoggerFactory.getLogger(RecordingTaskService.class);
    private final Database database;
    private final DeviceManager deviceManager;
    private final OssService ossService;

    // 用于执行下载任务的线程池
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);
    // 用于轮询下载进度的调度器
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // 任务状态常量
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_DOWNLOADING = "downloading";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_FAILED = "failed";

    public RecordingTaskService(Database database, DeviceManager deviceManager, OssService ossService) {
        this.database = database;
        this.deviceManager = deviceManager;
        this.ossService = ossService;

        // 启动任务恢复逻辑（可选，如重启后继续下载未完成任务）
        startStatusPolling();
    }

    /**
     * 启动进度轮询
     */
    private void startStatusPolling() {
        scheduler.scheduleAtFixedRate(this::pollTasksProgress, 5, 5, TimeUnit.SECONDS);
    }

    /**
     * 轮询处在下载中的任务进度
     */
    private void pollTasksProgress() {
        List<RecordingTask> downloadingTasks = getRecordingTasks(null, STATUS_DOWNLOADING);
        for (RecordingTask task : downloadingTasks) {
            if (task.getDownloadHandle() == null)
                continue;

            DeviceSDK sdk = deviceManager.getDeviceSDK(task.getDeviceId());
            if (sdk == null) {
                logger.warn("任务进度查询失败：设备 SDK 不存在, taskId={}, deviceId={}", task.getTaskId(), task.getDeviceId());
                continue;
            }

            int progress = sdk.getDownloadProgress(task.getDownloadHandle());
            if (progress >= 0 && progress <= 100) {
                task.setProgress(progress);
                if (progress == 100) {
                    handleDownloadComplete(task);
                } else {
                    updateRecordingTask(task.getTaskId(), task);
                }
            } else if (progress < 0) {
                logger.error("下载进度异常, taskId={}, progress={}", task.getTaskId(), progress);
                task.setStatus(STATUS_FAILED);
                task.setErrorMessage("SDK 下载进度检测异常");
                updateRecordingTask(task.getTaskId(), task);
                sdk.stopDownload(task.getDownloadHandle());
            }
        }
    }

    /**
     * 处理下载完成
     */
    private void handleDownloadComplete(RecordingTask task) {
        logger.info("录像下载完成, 开始上传 OSS: taskId={}, file={}", task.getTaskId(), task.getLocalFilePath());
        task.setStatus(STATUS_COMPLETED);

        // 停止 SDK 内部句柄
        DeviceSDK sdk = deviceManager.getDeviceSDK(task.getDeviceId());
        if (sdk != null && task.getDownloadHandle() != null) {
            sdk.stopDownload(task.getDownloadHandle());
        }

        // 异步上传 OSS
        CompletableFuture.runAsync(() -> {
            try {
                if (ossService != null && ossService.isEnabled()) {
                    File file = new File(task.getLocalFilePath());
                    if (file.exists()) {
                        String ossPath = "recordings/" + task.getDeviceId() + "/" + file.getName();
                        String url = ossService.uploadFile(task.getLocalFilePath(), ossPath);
                        if (url != null) {
                            task.setOssUrl(url);
                            logger.info("录像上传 OSS 成功: taskId={}, url={}", task.getTaskId(), url);
                        }
                    }
                }
                updateRecordingTask(task.getTaskId(), task);
            } catch (Exception e) {
                logger.error("录像上传 OSS 异常: taskId={}", task.getTaskId(), e);
                task.setErrorMessage("OSS 上传失败: " + e.getMessage());
                updateRecordingTask(task.getTaskId(), task);
            }
        });
    }

    /**
     * 获取录像任务列表
     */
    public List<RecordingTask> getRecordingTasks(String deviceId, String status) {
        List<RecordingTask> tasks = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM recording_tasks WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (deviceId != null && !deviceId.isEmpty()) {
            sql.append(" AND device_id = ?");
            params.add(deviceId);
        }

        if (status != null && !status.isEmpty()) {
            sql.append(" AND status = ?");
            params.add(status);
        }

        sql.append(" ORDER BY created_at DESC");

        try (Connection conn = database.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                tasks.add(RecordingTask.fromResultSet(rs));
            }
        } catch (SQLException e) {
            logger.error("获取录像任务列表失败", e);
        }
        return tasks;
    }

    /**
     * 获取录像任务详情
     */
    public RecordingTask getRecordingTask(String taskId) {
        String sql = "SELECT * FROM recording_tasks WHERE task_id = ?";
        try (Connection conn = database.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, taskId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return RecordingTask.fromResultSet(rs);
            }
        } catch (SQLException e) {
            logger.error("获取录像任务详情失败: {}", taskId, e);
        }
        return null;
    }

    /**
     * 创建录像任务
     */
    public RecordingTask createRecordingTask(RecordingTask task) {
        if (task.getTaskId() == null || task.getTaskId().isEmpty()) {
            task.setTaskId(UUID.randomUUID().toString());
        }
        String sql = "INSERT INTO recording_tasks (task_id, device_id, channel, start_time, end_time, status, progress, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
        try (Connection conn = database.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, task.getTaskId());
            pstmt.setString(2, task.getDeviceId());
            pstmt.setInt(3, task.getChannel());
            pstmt.setString(4, task.getStartTime());
            pstmt.setString(5, task.getEndTime());
            pstmt.setString(6, task.getStatus() != null ? task.getStatus() : STATUS_PENDING);
            pstmt.setInt(7, task.getProgress());
            pstmt.executeUpdate();
            return getRecordingTask(task.getTaskId());
        } catch (SQLException e) {
            logger.error("创建录像任务失败", e);
            return null;
        }
    }

    /**
     * 更新录像任务
     */
    public RecordingTask updateRecordingTask(String taskId, RecordingTask task) {
        String sql = "UPDATE recording_tasks SET local_file_path = ?, oss_url = ?, status = ?, progress = ?, download_handle = ?, error_message = ?, updated_at = CURRENT_TIMESTAMP WHERE task_id = ?";
        try (Connection conn = database.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, task.getLocalFilePath());
            pstmt.setString(2, task.getOssUrl());
            pstmt.setString(3, task.getStatus());
            pstmt.setInt(4, task.getProgress());
            if (task.getDownloadHandle() != null) {
                pstmt.setInt(5, task.getDownloadHandle());
            } else {
                pstmt.setNull(5, Types.INTEGER);
            }
            pstmt.setString(6, task.getErrorMessage());
            pstmt.setString(7, taskId);
            pstmt.executeUpdate();
            return getRecordingTask(taskId);
        } catch (SQLException e) {
            logger.error("更新录像任务失败: {}", taskId, e);
            return null;
        }
    }

    /**
     * 下载指定时间段录像 (异步)
     */
    public RecordingTask downloadRecording(String deviceId, int channel, String startTime, String endTime) {
        RecordingTask task = new RecordingTask();
        task.setDeviceId(deviceId);
        task.setChannel(channel);
        task.setStartTime(startTime);
        task.setEndTime(endTime);
        task.setStatus(STATUS_PENDING);
        task.setProgress(0);

        RecordingTask savedTask = createRecordingTask(task);
        if (savedTask != null) {
            // 提交到线程池异步执行下载逻辑
            executorService.submit(() -> executeDownload(savedTask));
        }
        return savedTask;
    }

    /**
     * 执行实际的下载逻辑
     */
    private void executeDownload(RecordingTask task) {
        try {
            logger.info("开始执行录像下载任务: taskId={}, deviceId={}", task.getTaskId(), task.getDeviceId());

            // 1. 确保设备在线并登录
            DeviceInfo device = deviceManager.getDevice(task.getDeviceId());
            if (device == null) {
                throw new Exception("设备不存在: " + task.getDeviceId());
            }

            if (!deviceManager.isDeviceLoggedIn(task.getDeviceId())) {
                if (!deviceManager.loginDevice(device)) {
                    throw new Exception("设备登录失败");
                }
            }

            int userId = deviceManager.getDeviceUserId(task.getDeviceId());
            DeviceSDK sdk = deviceManager.getDeviceSDK(task.getDeviceId());
            if (sdk == null) {
                throw new Exception("无法获取设备对应的 SDK");
            }

            // 2. 构造本地存储路径
            String fileName = String.format("%s_%d_%s_%s.mp4",
                    task.getDeviceId(), task.getChannel(),
                    task.getStartTime().replaceAll("[: -]", ""),
                    task.getEndTime().replaceAll("[: -]", ""));
            String localPath = new File("./storage/recordings", fileName).getAbsolutePath();
            task.setLocalFilePath(localPath);

            // 3. 解析时间
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date start = sdf.parse(task.getStartTime());
            Date end = sdf.parse(task.getEndTime());

            // 4. 调用 SDK 启动下载
            int handle = sdk.downloadPlaybackByTimeRange(userId, task.getChannel(), start, end, localPath, 0);
            if (handle < 0) {
                throw new Exception("SDK 启动下载失败, 错误码: " + sdk.getLastError());
            }

            // 5. 更新任务状态为下载中
            task.setDownloadHandle(handle);
            task.setStatus(STATUS_DOWNLOADING);
            updateRecordingTask(task.getTaskId(), task);

            logger.info("录像下载任务已加入 SDK 下载队列: taskId={}, handle={}", task.getTaskId(), handle);

        } catch (Exception e) {
            logger.error("录像下载任务启动异常: taskId={}", task.getTaskId(), e);
            task.setStatus(STATUS_FAILED);
            task.setErrorMessage(e.getMessage());
            updateRecordingTask(task.getTaskId(), task);
        }
    }
}
