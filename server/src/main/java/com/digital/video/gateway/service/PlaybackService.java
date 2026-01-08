package com.digital.video.gateway.service;

import com.digital.video.gateway.device.DeviceManager;
import com.digital.video.gateway.device.DeviceSDK;
import com.digital.video.gateway.database.DeviceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

/**
 * 回放查询服务
 * 根据设备品牌选择对应的SDK实现回放查询功能
 */
public class PlaybackService {
    private static final Logger logger = LoggerFactory.getLogger(PlaybackService.class);
    
    private final DeviceManager deviceManager;
    
    public PlaybackService(DeviceManager deviceManager) {
        this.deviceManager = deviceManager;
    }
    
    /**
     * 查询回放文件
     * @param deviceId 设备ID
     * @param channel 通道号
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 回放文件列表
     */
    public List<DeviceSDK.PlaybackFile> queryPlaybackFiles(String deviceId, int channel, Date startTime, Date endTime) {
        DeviceInfo device = deviceManager.getDevice(deviceId);
        if (device == null) {
            logger.warn("设备不存在: {}", deviceId);
            return List.of();
        }
        
        // 确保设备已登录
        if (!deviceManager.isDeviceLoggedIn(deviceId)) {
            if (!deviceManager.loginDevice(device)) {
                logger.warn("设备未登录，无法查询回放: {}", deviceId);
                return List.of();
            }
        }
        
        // 获取设备SDK
        DeviceSDK sdk = deviceManager.getDeviceSDK(deviceId);
        if (sdk == null) {
            logger.error("无法获取设备SDK: {}", deviceId);
            return List.of();
        }
        
        int userId = deviceManager.getDeviceUserId(deviceId);
        int actualChannel = channel > 0 ? channel : device.getChannel();
        
        try {
            // 验证时间范围
            if (startTime == null || endTime == null) {
                logger.error("开始时间和结束时间不能为空");
                return List.of();
            }
            
            if (startTime.after(endTime)) {
                logger.error("开始时间不能晚于结束时间");
                return List.of();
            }
            
            // 查询回放文件
            List<DeviceSDK.PlaybackFile> files = sdk.queryPlaybackFiles(userId, actualChannel, startTime, endTime);
            logger.info("回放查询成功: deviceId={}, channel={}, 文件数={}", deviceId, actualChannel, files.size());
            return files;
        } catch (Exception e) {
            logger.error("回放查询异常: deviceId={}, channel={}", deviceId, channel, e);
            return List.of();
        }
    }
}
