package com.digital.video.gateway.service;

import com.digital.video.gateway.config.Config;
import com.digital.video.gateway.database.DeviceInfo;
import com.digital.video.gateway.device.DeviceManager;
import com.digital.video.gateway.device.DeviceSDK;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 录制服务
 * 根据设备品牌选择对应的SDK实现录制功能
 */
public class RecorderService {
    private static final Logger logger = LoggerFactory.getLogger(RecorderService.class);
    
    private final DeviceManager deviceManager;
    private final Config.RecorderConfig config;
    private final Map<String, RecordingSession> recordingSessions; // deviceId -> RecordingSession
    
    public RecorderService(DeviceManager deviceManager, Config.RecorderConfig config) {
        this.deviceManager = deviceManager;
        this.config = config;
        this.recordingSessions = new ConcurrentHashMap<>();
    }
    
    /**
     * 启动录制
     */
    public boolean startRecording(String deviceId) {
        if (!config.isEnabled()) {
            logger.debug("录制功能已禁用");
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
        
        // 确保设备已登录
        if (!deviceManager.isDeviceLoggedIn(deviceId)) {
            if (!deviceManager.loginDevice(device)) {
                logger.warn("设备未登录，无法启动录制: {}", deviceId);
                return false;
            }
        }
        
        // 获取设备SDK
        DeviceSDK sdk = deviceManager.getDeviceSDK(deviceId);
        if (sdk == null) {
            logger.error("无法获取设备SDK: {}", deviceId);
            return false;
        }
        
        int userId = deviceManager.getDeviceUserId(deviceId);
        int channel = device.getChannel() > 0 ? device.getChannel() : 1;
        
        try {
            // 启动预览
            int connectId = sdk.startRealPlay(userId, channel, 0); // 0=主码流
            if (connectId < 0) {
                logger.error("启动预览失败，无法录制: deviceId={}", deviceId);
                return false;
            }
            
            // 生成录制文件名
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
            String timestamp = sdf.format(new Date());
            String filePath = config.getRecordPath() + "/record_" +
                deviceId.replace(".", "_").replace(":", "_") + "_" + timestamp + ".sdv";
            
            // 确保目录存在
            File recordDir = new File(config.getRecordPath());
            if (!recordDir.exists()) {
                recordDir.mkdirs();
            }
            
            // 开始录制
            boolean result = sdk.startRecording(connectId, filePath);
            if (result) {
                RecordingSession session = new RecordingSession(deviceId, userId, connectId, filePath);
                RecordingSession existing = recordingSessions.putIfAbsent(deviceId, session);
                if (existing != null) {
                    sdk.stopRecording(connectId);
                    sdk.stopRealPlay(connectId);
                    logger.debug("设备 {} 已在录制中（并发竞争已忽略本次启动）", deviceId);
                    return true;
                }
                logger.info("设备 {} 录制已启动: {}", deviceId, filePath);
                return true;
            } else {
                // 录制失败，停止预览
                sdk.stopRealPlay(connectId);
                logger.error("设备 {} 录制启动失败", deviceId);
                return false;
            }
        } catch (Exception e) {
            logger.error("启动录制异常: deviceId={}", deviceId, e);
            return false;
        }
    }
    
    /**
     * 停止录制
     */
    public boolean stopRecording(String deviceId) {
        RecordingSession session = recordingSessions.remove(deviceId);
        if (session == null) {
            logger.debug("设备 {} 未在录制中", deviceId);
            return false;
        }
        
        DeviceSDK sdk = deviceManager.getDeviceSDK(deviceId);
        if (sdk == null) {
            logger.error("无法获取设备SDK: {}", deviceId);
            return false;
        }
        
        try {
            // 停止录制
            boolean stopRecordResult = sdk.stopRecording(session.getConnectId());
            boolean stopPreviewResult = sdk.stopRealPlay(session.getConnectId());
            if (!stopPreviewResult) {
                logger.warn("设备 {} 录制停止后预览释放失败: connectId={}", deviceId, session.getConnectId());
            }
            if (stopRecordResult) {
                logger.info("设备 {} 录制已停止并释放预览: {}", deviceId, session.getFilePath());
                return true;
            }
            logger.error("设备 {} 录制停止失败", deviceId);
            return false;
        } catch (Exception e) {
            logger.error("停止录制异常: deviceId={}", deviceId, e);
            return false;
        }
    }
    
    /**
     * 获取当前录制文件
     */
    public String getCurrentRecordingFile(String deviceId) {
        RecordingSession session = recordingSessions.get(deviceId);
        if (session != null && session.getFilePath() != null) {
            File file = new File(session.getFilePath());
            if (file.exists()) {
                return file.getAbsolutePath();
            }
        }
        return null;
    }
    
    /**
     * 录制会话信息
     */
    private static class RecordingSession {
        private final String deviceId;
        private final int userId;
        private final int connectId;
        private final String filePath;
        private final Date startTime;
        
        public RecordingSession(String deviceId, int userId, int connectId, String filePath) {
            this.deviceId = deviceId;
            this.userId = userId;
            this.connectId = connectId;
            this.filePath = filePath;
            this.startTime = new Date();
        }
        
        public String getDeviceId() { return deviceId; }
        public int getUserId() { return userId; }
        public int getConnectId() { return connectId; }
        public String getFilePath() { return filePath; }
        public Date getStartTime() { return startTime; }
    }
}
