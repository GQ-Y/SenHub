package com.digital.video.gateway.recorder;

import com.digital.video.gateway.config.Config;
import com.digital.video.gateway.service.RecorderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 录制管理器
 * 负责管理所有设备的自动录制功能
 * 使用RecorderService实现多品牌SDK支持
 */
public class Recorder {
    private static final Logger logger = LoggerFactory.getLogger(Recorder.class);

    private final RecorderService recorderService;
    private final Config.RecorderConfig config;
    private ScheduledExecutorService cleanupScheduler;

    public Recorder(RecorderService recorderService, Config.RecorderConfig config) {
        this.recorderService = recorderService;
        this.config = config;
    }

    /**
     * 启动录制管理器
     */
    public void start() {
        if (!config.isEnabled()) {
            logger.info("录制功能已禁用");
            return;
        }

        // 确保录制目录存在
        File recordDir = new File(config.getRecordPath());
        if (!recordDir.exists()) {
            recordDir.mkdirs();
            logger.info("创建录制目录: {}", config.getRecordPath());
        }

        // 启动清理任务（循环存储）
        cleanupScheduler = Executors.newSingleThreadScheduledExecutor();
        cleanupScheduler.scheduleAtFixedRate(this::cleanupOldRecords, 60, 60, TimeUnit.SECONDS);

        logger.info("录制管理器已启动，录制路径: {}, 保留时长: {}分钟", 
            config.getRecordPath(), config.getRetentionMinutes());
    }

    /**
     * 停止录制管理器
     */
    public void stop() {
        if (cleanupScheduler != null) {
            cleanupScheduler.shutdownNow();
        }

        logger.info("录制管理器已停止");
    }

    /**
     * 为设备启动录制
     */
    public boolean startRecording(String deviceId) {
        return recorderService.startRecording(deviceId);
    }

    /**
     * 停止设备录制
     */
    public void stopRecording(String deviceId) {
        recorderService.stopRecording(deviceId);
    }

    /**
     * 获取设备当前正在录制的文件路径
     * @param deviceId 设备ID
     * @return 当前正在录制的文件路径，如果未在录制则返回null
     */
    public String getCurrentRecordingFile(String deviceId) {
        return recorderService.getCurrentRecordingFile(deviceId);
    }

    /**
     * 获取设备的录制文件路径（根据时间范围）
     * @param deviceId 设备ID
     * @param targetTime 目标时间（通常是系统时间前1分钟）
     * @return 录制文件路径，如果不存在则返回null
     */
    public String getRecordFile(String deviceId, Date targetTime) {
        if (!config.isEnabled()) {
            return null;
        }

        // 计算时间范围：目标时间前后30秒（共1分钟）
        // 这样可以获取目标时间点前后30秒的视频片段
        Calendar cal = Calendar.getInstance();
        cal.setTime(targetTime);
        cal.add(Calendar.SECOND, -30); // 前30秒
        Date startTime = cal.getTime();
        cal.add(Calendar.SECOND, 60); // 后30秒（总共1分钟）
        Date endTime = cal.getTime();

        // 在录制目录中查找匹配的文件（支持MP4和FLV格式，优先FLV）
        File recordDir = new File(config.getRecordPath());
        File[] files = recordDir.listFiles((dir, name) -> {
            String prefix = "record_" + deviceId.replace(".", "_");
                return name.startsWith(prefix) && name.endsWith(".mp4");
        });

        if (files == null || files.length == 0) {
            return null;
        }

        // 按修改时间排序，查找最接近的文件
        // 注意：录制文件的lastModified时间表示录制开始时间
        Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));

        // 查找在时间范围内的文件
        // 由于录制是连续的，我们查找录制开始时间在目标时间范围内的文件
        for (File file : files) {
            long fileStartTime = file.lastModified();
            // 假设每个录制文件至少持续2分钟（根据配置）
            // 如果文件的开始时间在目标时间范围内，或者文件覆盖了目标时间，则返回该文件
            long fileEndTime = fileStartTime + (2 * 60 * 1000); // 2分钟
            
            // 检查目标时间是否在文件的录制时间范围内
            if (targetTime.getTime() >= fileStartTime && targetTime.getTime() <= fileEndTime) {
                return file.getAbsolutePath();
            }
        }

        return null;
    }

    /**
     * 清理旧录制文件（循环存储）
     */
    private void cleanupOldRecords() {
        try {
            File recordDir = new File(config.getRecordPath());
            if (!recordDir.exists()) {
                return;
            }

            long retentionMillis = config.getRetentionMinutes() * 60 * 1000L;
            long cutoffTime = System.currentTimeMillis() - retentionMillis;

                File[] files = recordDir.listFiles((dir, name) -> name.endsWith(".mp4"));
            if (files == null) {
                return;
            }

            int deletedCount = 0;
            for (File file : files) {
                if (file.lastModified() < cutoffTime) {
                    if (file.delete()) {
                        deletedCount++;
                    }
                }
            }

            if (deletedCount > 0) {
                logger.info("清理了 {} 个旧录制文件", deletedCount);
            }
        } catch (Exception e) {
            logger.error("清理旧录制文件失败", e);
        }
    }

}
