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
    private ConfigService configService;
    
    // HTTP客户端用于Webhook推送
    private final java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();
    
    // 任务是否需要发送Webhook通知的标记 (key: taskId, value: webhookEnabled)
    private final java.util.concurrent.ConcurrentHashMap<String, Boolean> taskWebhookSettings = new java.util.concurrent.ConcurrentHashMap<>();
    /** 仅下载不上传 OSS 的 taskId 集合（用于工作流录像节点：先下载前后两段再合并后单独上传） */
    private final java.util.concurrent.ConcurrentHashMap<String, Boolean> taskSkipOssUpload = new java.util.concurrent.ConcurrentHashMap<>();
    // 下载进度读取失败计数（短暂抖动容错，避免一次异常就把任务打成失败）
    private final java.util.concurrent.ConcurrentHashMap<String, Integer> downloadProgressFailCount = new java.util.concurrent.ConcurrentHashMap<>();

    // 用于执行下载任务的线程池
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);
    // 用于轮询下载进度的调度器
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    // 下载并发闸门：避免批量任务同时抢占磁盘与SDK资源导致抖动
    private final Semaphore downloadConcurrencyLimiter = new Semaphore(3);

    // 任务状态常量
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_DOWNLOADING = 1;
    public static final int STATUS_COMPLETED = 2;
    public static final int STATUS_FAILED = 3;

    public RecordingTaskService(Database database, DeviceManager deviceManager, OssService ossService) {
        this.database = database;
        this.deviceManager = deviceManager;
        this.ossService = ossService;

        // 启动任务恢复逻辑（可选，如重启后继续下载未完成任务）
        startStatusPolling();
    }
    
    /**
     * 设置配置服务（用于获取Webhook配置）
     */
    public void setConfigService(ConfigService configService) {
        this.configService = configService;
    }

    /**
     * 关闭服务：停止调度器与任务线程池（用于进程优雅退出）
     */
    public void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            try {
                scheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        logger.info("录像任务服务已关闭");
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
        List<RecordingTask> downloadingTasks = getRecordingTasks(null, String.valueOf(STATUS_DOWNLOADING));
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
                downloadProgressFailCount.remove(task.getTaskId());
                task.setProgress(progress);
                if (progress == 100) {
                    handleDownloadComplete(task);
                } else {
                    updateRecordingTask(task.getTaskId(), task);
                }
            } else if (progress < 0) {
                int failCount = downloadProgressFailCount.compute(task.getTaskId(),
                        (k, v) -> v == null ? 1 : v + 1);
                if (failCount < 3) {
                    logger.warn("下载进度异常(可恢复)，等待下次轮询: taskId={}, progress={}, failCount={}",
                            task.getTaskId(), progress, failCount);
                    continue;
                }
                logger.error("下载进度连续异常，任务置失败: taskId={}, progress={}, failCount={}",
                        task.getTaskId(), progress, failCount);
                task.setStatus(STATUS_FAILED);
                task.setErrorMessage("SDK 下载进度检测异常(连续失败)");
                updateRecordingTask(task.getTaskId(), task);
                sdk.stopDownload(task.getDownloadHandle());
                downloadProgressFailCount.remove(task.getTaskId());
            }
        }
    }

    /**
     * 处理下载完成
     */
    private void handleDownloadComplete(RecordingTask task) {
        logger.info("录像下载完成, 开始处理: taskId={}, file={}", task.getTaskId(), task.getLocalFilePath());
        task.setStatus(STATUS_COMPLETED);
        downloadProgressFailCount.remove(task.getTaskId());

        // 停止 SDK 内部句柄
        DeviceSDK sdk = deviceManager.getDeviceSDK(task.getDeviceId());
        if (sdk != null && task.getDownloadHandle() != null) {
            sdk.stopDownload(task.getDownloadHandle());
        }

        // 仅下载不上传（工作流录像节点会自行合并后上传）
        if (Boolean.TRUE.equals(taskSkipOssUpload.remove(task.getTaskId()))) {
            updateRecordingTask(task.getTaskId(), task);
            return;
        }
        // 异步转码并上传 OSS
        CompletableFuture.runAsync(() -> {
            try {
                String localFilePath = task.getLocalFilePath();
                File originalFile = new File(localFilePath);
                
                // 检查是否需要转码（海康私有格式以 IMKH 开头）
                if (originalFile.exists() && needsTranscoding(originalFile)) {
                    logger.info("检测到海康私有格式，开始ffmpeg转码: taskId={}", task.getTaskId());
                    String transcodedPath = transcodeToMp4(localFilePath);
                    if (transcodedPath != null) {
                        // 转码成功，更新文件路径
                        task.setLocalFilePath(transcodedPath);
                        localFilePath = transcodedPath;
                        logger.info("ffmpeg转码成功: taskId={}, newPath={}", task.getTaskId(), transcodedPath);
                        
                        // 删除原始私有格式文件
                        if (originalFile.delete()) {
                            logger.debug("已删除原始私有格式文件: {}", originalFile.getAbsolutePath());
                        }
                    } else {
                        logger.warn("ffmpeg转码失败，将使用原始文件: taskId={}", task.getTaskId());
                    }
                }
                
                // 上传到 OSS
                String videoUrl = null;
                if (ossService != null && ossService.isEnabled()) {
                    File file = new File(localFilePath);
                    if (file.exists()) {
                        String ossPath = "recordings/" + task.getDeviceId() + "/" + file.getName();
                        videoUrl = ossService.uploadFile(localFilePath, ossPath);
                        if (videoUrl != null) {
                            task.setOssUrl(videoUrl);
                            logger.info("录像上传 OSS 成功: taskId={}, url={}", task.getTaskId(), videoUrl);
                        }
                    }
                }
                updateRecordingTask(task.getTaskId(), task);
                
                // 清理webhook设置（Webhook通知现在由工作流的webhook节点处理）
                taskWebhookSettings.remove(task.getTaskId());
            } catch (Exception e) {
                logger.error("录像处理异常: taskId={}", task.getTaskId(), e);
                task.setErrorMessage("录像处理失败: " + e.getMessage());
                updateRecordingTask(task.getTaskId(), task);
            }
        });
    }
    
    /**
     * 检查视频文件是否需要转码（海康私有格式）
     * 海康私有PS流格式以 "IMKH" 开头，标准MP4以 "ftyp" 开头
     */
    private boolean needsTranscoding(File file) {
        try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
            byte[] header = new byte[4];
            if (fis.read(header) == 4) {
                // 检查是否为海康私有格式 (IMKH)
                if (header[0] == 'I' && header[1] == 'M' && header[2] == 'K' && header[3] == 'H') {
                    return true;
                }
                // 检查是否已经是标准MP4 (ftyp)
                if (header[0] == 0x00 && header[1] == 0x00 && header[2] == 0x00 && 
                    (header[3] == 0x18 || header[3] == 0x1C || header[3] == 0x20)) {
                    // 可能是MP4，读取更多字节确认
                    byte[] ftypCheck = new byte[4];
                    if (fis.read(ftypCheck) == 4) {
                        if (ftypCheck[0] == 'f' && ftypCheck[1] == 't' && ftypCheck[2] == 'y' && ftypCheck[3] == 'p') {
                            return false; // 已经是标准MP4
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("检查视频格式异常: {}", file.getAbsolutePath(), e);
        }
        // 默认需要转码（保守策略）
        return true;
    }
    
    /**
     * 使用ffmpeg将视频转码为标准MP4格式
     * @param inputPath 输入文件路径
     * @return 转码后的文件路径，失败返回null
     */
    private String transcodeToMp4(String inputPath) {
        try {
            File inputFile = new File(inputPath);
            String baseName = inputFile.getName();
            // 生成转码后的文件名（添加 _converted 后缀）
            String outputName;
            if (baseName.toLowerCase().endsWith(".mp4")) {
                outputName = baseName.substring(0, baseName.length() - 4) + "_converted.mp4";
            } else {
                outputName = baseName + "_converted.mp4";
            }
            String outputPath = inputFile.getParent() + File.separator + outputName;
            
            // 构建ffmpeg命令
            // -y: 覆盖输出文件
            // -i: 输入文件
            // -c:v copy: 视频流直接复制（不重新编码，速度快）
            // -c:a aac: 音频转为AAC格式（兼容性好）
            // -movflags +faststart: 将moov原子移到文件开头，便于流式播放
            ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-y", "-i", inputPath,
                "-c:v", "copy", "-c:a", "aac", 
                "-movflags", "+faststart",
                outputPath
            );
            pb.redirectErrorStream(true);
            
            logger.info("执行ffmpeg转码: {} -> {}", inputPath, outputPath);
            Process process = pb.start();
            
            // 读取ffmpeg输出（避免缓冲区满导致阻塞）
            StringBuilder output = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            // 等待进程完成（最多等待5分钟）
            boolean finished = process.waitFor(300, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                logger.error("ffmpeg转码超时（5分钟），已强制终止");
                return null;
            }
            
            int exitCode = process.exitValue();
            if (exitCode == 0) {
                File outputFile = new File(outputPath);
                if (outputFile.exists() && outputFile.length() > 0) {
                    logger.info("ffmpeg转码完成: exitCode={}, outputSize={}", exitCode, outputFile.length());
                    return outputPath;
                } else {
                    logger.error("ffmpeg转码输出文件不存在或为空: {}", outputPath);
                    return null;
                }
            } else {
                logger.error("ffmpeg转码失败: exitCode={}, output={}", exitCode, 
                        output.length() > 500 ? output.substring(output.length() - 500) : output);
                return null;
            }
        } catch (Exception e) {
            logger.error("ffmpeg转码异常: {}", inputPath, e);
            return null;
        }
    }
    
    /**
     * 发送录像完成的Webhook通知
     */
    private void sendVideoWebhookNotification(RecordingTask task, String videoUrl) {
        if (configService == null) {
            logger.debug("ConfigService未设置，跳过录像Webhook通知");
            return;
        }
        
        try {
            com.digital.video.gateway.config.Config config = configService.getConfig();
            if (config == null || config.getNotification() == null) {
                return;
            }
            
            com.digital.video.gateway.config.Config.NotificationConfig notificationConfig = config.getNotification();
            String webhookUrl = null;
            String channelType = null;
            
            // 获取启用的Webhook配置
            if (notificationConfig.getWechat() != null && notificationConfig.getWechat().isEnabled()) {
                webhookUrl = notificationConfig.getWechat().getWebhookUrl();
                channelType = "wechat";
            } else if (notificationConfig.getDingtalk() != null && notificationConfig.getDingtalk().isEnabled()) {
                webhookUrl = notificationConfig.getDingtalk().getWebhookUrl();
                channelType = "dingtalk";
            } else if (notificationConfig.getFeishu() != null && notificationConfig.getFeishu().isEnabled()) {
                webhookUrl = notificationConfig.getFeishu().getWebhookUrl();
                channelType = "feishu";
            }
            
            if (webhookUrl == null || webhookUrl.isEmpty()) {
                logger.debug("未配置Webhook，跳过录像通知");
                return;
            }
            
            // 构建通知消息
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String timeStr = java.time.LocalDateTime.now().format(formatter);
            
            StringBuilder content = new StringBuilder();
            content.append("【录像回放已就绪】\n");
            content.append("设备ID: ").append(task.getDeviceId()).append("\n");
            content.append("时间范围: ").append(task.getStartTime()).append(" ~ ").append(task.getEndTime()).append("\n");
            content.append("完成时间: ").append(timeStr).append("\n");
            content.append("录像地址: ").append(videoUrl);
            
            String jsonBody = buildWebhookBody(channelType, content.toString());
            
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(webhookUrl))
                    .timeout(java.time.Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            
            java.net.http.HttpResponse<String> response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                logger.info("录像Webhook通知发送成功: taskId={}, deviceId={}", task.getTaskId(), task.getDeviceId());
            } else {
                logger.warn("录像Webhook通知发送失败: status={}, body={}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            logger.error("发送录像Webhook通知异常: taskId={}", task.getTaskId(), e);
        }
    }
    
    /**
     * 构建Webhook请求体
     */
    private String buildWebhookBody(String channelType, String message) {
        if ("wechat".equals(channelType)) {
            return "{\"msgtype\":\"text\",\"text\":{\"content\":\"" + escapeJson(message) + "\"}}";
        } else if ("dingtalk".equals(channelType)) {
            return "{\"msgtype\":\"text\",\"text\":{\"content\":\"" + escapeJson(message) + "\"}}";
        } else if ("feishu".equals(channelType)) {
            return "{\"msg_type\":\"text\",\"content\":{\"text\":\"" + escapeJson(message) + "\"}}";
        }
        return "{\"message\":\"" + escapeJson(message) + "\"}";
    }
    
    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
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
            try {
                int statusInt = Integer.parseInt(status);
                sql.append(" AND status = ?");
                params.add(statusInt);
            } catch (NumberFormatException e) {
                logger.warn("无效的状态参数: {}", status);
            }
        }

        sql.append(" ORDER BY created_at DESC");

        Connection conn = database.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
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
        Connection conn = database.getConnection(); try (
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
        Connection conn = database.getConnection(); try (
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, task.getTaskId());
            pstmt.setString(2, task.getDeviceId());
            pstmt.setInt(3, task.getChannel());
            pstmt.setString(4, task.getStartTime());
            pstmt.setString(5, task.getEndTime());
            pstmt.setInt(6, task.getStatus());
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
        Connection conn = database.getConnection(); try (
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, task.getLocalFilePath());
            pstmt.setString(2, task.getOssUrl());
            pstmt.setInt(3, task.getStatus());
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
        return downloadRecordingWithOptions(deviceId, channel, startTime, endTime, false);
    }
    
    /**
     * 下载指定时间段录像 (同步等待完成)
     * 
     * @param deviceId 设备ID
     * @param channel 通道号
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param timeoutSeconds 超时时间（秒）
     * @return 完成的录像任务，如果超时或失败返回对应状态的任务
     */
    public RecordingTask downloadRecordingSync(String deviceId, int channel, String startTime, String endTime, int timeoutSeconds) {
        return downloadRecordingSync(deviceId, channel, startTime, endTime, timeoutSeconds, true);
    }

    /**
     * 下载指定时间段录像 (同步等待完成)，可选是否上传 OSS。
     * @param uploadToOss false 时仅下载到本地，不转码、不上传（用于工作流中前后两段合并后统一上传）
     */
    public RecordingTask downloadRecordingSync(String deviceId, int channel, String startTime, String endTime, int timeoutSeconds, boolean uploadToOss) {
        RecordingTask task = new RecordingTask();
        task.setDeviceId(deviceId);
        task.setChannel(channel);
        task.setStartTime(startTime);
        task.setEndTime(endTime);
        task.setStatus(STATUS_PENDING);
        task.setProgress(0);

        RecordingTask savedTask = createRecordingTask(task);
        if (savedTask == null) {
            logger.error("创建录像任务失败: deviceId={}", deviceId);
            return null;
        }
        if (!uploadToOss) {
            taskSkipOssUpload.put(savedTask.getTaskId(), Boolean.TRUE);
        }
        String taskId = savedTask.getTaskId();
        logger.info("同步录像下载开始: taskId={}, deviceId={}, 超时={}秒, uploadToOss={}", taskId, deviceId, timeoutSeconds, uploadToOss);
        
        // 提交到线程池执行下载
        executorService.submit(() -> executeDownload(savedTask));
        
        // 轮询等待完成
        long startTimeMs = System.currentTimeMillis();
        long timeoutMs = timeoutSeconds * 1000L;
        
        while (System.currentTimeMillis() - startTimeMs < timeoutMs) {
            try {
                Thread.sleep(2000);  // 每2秒检查一次
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            
            RecordingTask current = getRecordingTask(taskId);
            if (current == null) {
                logger.warn("录像任务不存在: taskId={}", taskId);
                break;
            }
            
            int status = current.getStatus();
            if (status == STATUS_COMPLETED || status == STATUS_FAILED) {
                logger.info("同步录像下载完成: taskId={}, status={}, 耗时={}ms", 
                        taskId, status, System.currentTimeMillis() - startTimeMs);
                return current;
            }
            
            long elapsed = (System.currentTimeMillis() - startTimeMs) / 1000;
            logger.info("录像下载中: taskId={}, status={}, progress={}%, 已等待{}秒/{}秒",
                    taskId, current.getStatus(), current.getProgress(), elapsed, timeoutSeconds);
        }
        
        logger.warn("同步录像下载超时: taskId={}, 已等待{}秒", taskId, timeoutSeconds);
        RecordingTask timeoutTask = getRecordingTask(taskId);
        return timeoutTask;
    }
    
    /**
     * 下载指定时间段录像 (异步)，支持配置录像完成后是否发送Webhook通知
     * 
     * @param deviceId 设备ID
     * @param channel 通道号
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param webhookEnabled 录像上传完成后是否发送Webhook通知
     */
    public RecordingTask downloadRecordingWithOptions(String deviceId, int channel, String startTime, String endTime, boolean webhookEnabled) {
        RecordingTask task = new RecordingTask();
        task.setDeviceId(deviceId);
        task.setChannel(channel);
        task.setStartTime(startTime);
        task.setEndTime(endTime);
        task.setStatus(STATUS_PENDING);
        task.setProgress(0);

        RecordingTask savedTask = createRecordingTask(task);
        if (savedTask != null) {
            // 保存webhook配置
            taskWebhookSettings.put(savedTask.getTaskId(), webhookEnabled);
            logger.debug("录像任务创建: taskId={}, webhookEnabled={}", savedTask.getTaskId(), webhookEnabled);
            // 提交到线程池异步执行下载逻辑
            executorService.submit(() -> executeDownload(savedTask));
        }
        return savedTask;
    }

    /**
     * 执行实际的下载逻辑
     */
    private void executeDownload(RecordingTask task) {
        boolean permitAcquired = false;
        try {
            logger.info("开始执行录像下载任务: taskId={}, deviceId={}", task.getTaskId(), task.getDeviceId());
            permitAcquired = downloadConcurrencyLimiter.tryAcquire(30, TimeUnit.SECONDS);
            if (!permitAcquired) {
                throw new Exception("下载并发繁忙，请稍后重试");
            }

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
        } finally {
            if (permitAcquired) {
                downloadConcurrencyLimiter.release();
            }
        }
    }
}
