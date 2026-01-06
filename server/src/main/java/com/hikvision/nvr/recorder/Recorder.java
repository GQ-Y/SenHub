package com.hikvision.nvr.recorder;

import com.hikvision.nvr.config.Config;
import com.hikvision.nvr.database.DeviceInfo;
import com.hikvision.nvr.device.DeviceManager;
import com.hikvision.nvr.hikvision.HCNetSDK;
import com.hikvision.nvr.hikvision.HikvisionSDK;
import com.sun.jna.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 录制管理器
 * 负责管理所有设备的自动录制功能
 */
public class Recorder {
    private static final Logger logger = LoggerFactory.getLogger(Recorder.class);

    private final HikvisionSDK sdk;
    private final DeviceManager deviceManager;
    private final Config.RecorderConfig config;
    private final Map<String, RecordingSession> recordingSessions; // deviceId -> RecordingSession
    private ScheduledExecutorService cleanupScheduler;

    public Recorder(HikvisionSDK sdk, DeviceManager deviceManager, Config.RecorderConfig config) {
        this.sdk = sdk;
        this.deviceManager = deviceManager;
        this.config = config;
        this.recordingSessions = new ConcurrentHashMap<>();
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

        // 停止所有正在进行的录制
        for (RecordingSession session : recordingSessions.values()) {
            stopRecording(session.getDeviceId());
        }
        recordingSessions.clear();

        logger.info("录制管理器已停止");
    }

    /**
     * 为设备启动录制
     */
    public boolean startRecording(String deviceId) {
        if (!config.isEnabled()) {
            return false;
        }

        if (recordingSessions.containsKey(deviceId)) {
            logger.debug("设备 {} 已在录制中", deviceId);
            return true;
        }

        DeviceInfo device = deviceManager.getDevice(deviceId);
        if (device == null) {
            logger.warn("设备不存在，无法启动录制: {}", deviceId);
            return false;
        }

        int userId = deviceManager.getDeviceUserId(deviceId);
        if (userId < 0) {
            logger.warn("设备未登录，无法启动录制: {}", deviceId);
            return false;
        }

        try {
            int channel = device.getChannel() > 0 ? device.getChannel() : 1;
            RecordingSession session = new RecordingSession(deviceId, userId, channel, config);
            
            if (session.start(sdk.getSDK())) {
                recordingSessions.put(deviceId, session);
                logger.info("设备 {} 录制已启动", deviceId);
                return true;
            } else {
                logger.error("设备 {} 录制启动失败", deviceId);
                return false;
            }
        } catch (Exception e) {
            logger.error("启动录制异常: {}", deviceId, e);
            return false;
        }
    }

    /**
     * 停止设备录制
     */
    public void stopRecording(String deviceId) {
        RecordingSession session = recordingSessions.remove(deviceId);
        if (session != null) {
            session.stop(sdk.getSDK());
            logger.info("设备 {} 录制已停止", deviceId);
        }
    }

    /**
     * 获取设备当前正在录制的文件路径
     * @param deviceId 设备ID
     * @return 当前正在录制的文件路径，如果未在录制则返回null
     */
    public String getCurrentRecordingFile(String deviceId) {
        RecordingSession session = recordingSessions.get(deviceId);
        if (session != null && session.getRecordFilePath() != null) {
            File file = new File(session.getRecordFilePath());
            if (file.exists()) {
                return file.getAbsolutePath();
            }
        }
        return null;
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

        // 在录制目录中查找匹配的文件
        File recordDir = new File(config.getRecordPath());
        File[] files = recordDir.listFiles((dir, name) -> 
            name.startsWith("record_" + deviceId.replace(".", "_")) && name.endsWith(".mp4"));

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

    /**
     * 录制会话类
     */
    private static class RecordingSession {
        private final String deviceId;
        private final int userId;
        private final int channel;
        private final Config.RecorderConfig config;
        private int realPlayHandle = -1;
        private String recordFilePath;
        private Date startTime;

        public RecordingSession(String deviceId, int userId, int channel, Config.RecorderConfig config) {
            this.deviceId = deviceId;
            this.userId = userId;
            this.channel = channel;
            this.config = config;
        }

        public boolean start(HCNetSDK hcNetSDK) {
            try {
                // 设置预览参数
                HCNetSDK.NET_DVR_PREVIEWINFO previewInfo = new HCNetSDK.NET_DVR_PREVIEWINFO();
                previewInfo.lChannel = channel;
                previewInfo.dwStreamType = 0; // 主码流
                previewInfo.dwLinkMode = 0; // TCP方式
                previewInfo.bBlocked = 1; // 阻塞取流
                previewInfo.byProtoType = 0; // 私有协议
                previewInfo.write();

                // 创建回调函数（可以为null，因为使用NET_DVR_SaveRealData会自动保存数据）
                HCNetSDK.FRealDataCallBack_V30 realDataCallback = null;

                // 启动预览
                realPlayHandle = hcNetSDK.NET_DVR_RealPlay_V40(userId, previewInfo, realDataCallback, null);
                if (realPlayHandle == -1) {
                    return false;
                }

                // 等待预览稳定
                Thread.sleep(1000);

                // 生成录制文件名
                String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
                recordFilePath = config.getRecordPath() + "/record_" + 
                    deviceId.replace(".", "_") + "_" + timestamp + ".mp4";

                // 开始录制（使用NET_DVR_SaveRealData，直接传文件路径字符串）
                boolean saveResult = hcNetSDK.NET_DVR_SaveRealData(realPlayHandle, recordFilePath);
                if (!saveResult) {
                    hcNetSDK.NET_DVR_StopRealPlay(realPlayHandle);
                    realPlayHandle = -1;
                    return false;
                }

                startTime = new Date();
                return true;

            } catch (Exception e) {
                logger.error("启动录制会话失败: {}", deviceId, e);
                if (realPlayHandle != -1) {
                    hcNetSDK.NET_DVR_StopRealPlay(realPlayHandle);
                    realPlayHandle = -1;
                }
                return false;
            }
        }

        public void stop(HCNetSDK hcNetSDK) {
            if (realPlayHandle != -1) {
                try {
                    hcNetSDK.NET_DVR_StopSaveRealData(realPlayHandle);
                    Thread.sleep(500);
                    hcNetSDK.NET_DVR_StopRealPlay(realPlayHandle);
                } catch (Exception e) {
                    logger.error("停止录制会话失败: {}", deviceId, e);
                }
                realPlayHandle = -1;
            }
        }

        public String getDeviceId() {
            return deviceId;
        }

        public String getRecordFilePath() {
            return recordFilePath;
        }

        public Date getStartTime() {
            return startTime;
        }
    }
}
