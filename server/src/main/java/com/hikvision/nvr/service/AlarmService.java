package com.hikvision.nvr.service;

import com.hikvision.nvr.device.DeviceManager;
import com.hikvision.nvr.database.DeviceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 报警服务
 * 处理设备报警信号，自动触发抓图
 */
public class AlarmService {
    private static final Logger logger = LoggerFactory.getLogger(AlarmService.class);
    
    private final DeviceManager deviceManager;
    private final CaptureService captureService;
    private final OssService ossService;
    private boolean enabled = true;
    
    public AlarmService(DeviceManager deviceManager, CaptureService captureService, OssService ossService) {
        this.deviceManager = deviceManager;
        this.captureService = captureService;
        this.ossService = ossService;
    }
    
    /**
     * 设置是否启用报警自动抓图
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        logger.info("报警自动抓图功能已{}", enabled ? "启用" : "禁用");
    }
    
    /**
     * 处理报警事件
     * @param deviceId 设备ID
     * @param channel 通道号
     * @param alarmType 报警类型
     * @param alarmMessage 报警消息
     */
    public void handleAlarm(String deviceId, int channel, String alarmType, String alarmMessage) {
        if (!enabled) {
            logger.debug("报警自动抓图功能已禁用，跳过处理: deviceId={}, alarmType={}", deviceId, alarmType);
            return;
        }
        
        logger.info("收到报警信号: deviceId={}, channel={}, alarmType={}, message={}", 
            deviceId, channel, alarmType, alarmMessage);
        
        try {
            // 获取设备信息
            DeviceInfo device = deviceManager.getDevice(deviceId);
            if (device == null) {
                logger.warn("设备不存在，无法处理报警: {}", deviceId);
                return;
            }
            
            // 确保设备在线
            if (!"online".equals(device.getStatus())) {
                logger.warn("设备不在线，无法处理报警: deviceId={}, status={}", deviceId, device.getStatus());
                return;
            }
            
            // 执行自动抓图
            int actualChannel = channel > 0 ? channel : device.getChannel();
            String capturePath = captureService.captureSnapshot(deviceId, actualChannel);
            
            if (capturePath != null) {
                logger.info("报警自动抓图成功: deviceId={}, channel={}, filePath={}", 
                    deviceId, actualChannel, capturePath);
                
                // 如果OSS服务可用，自动上传
                if (ossService != null && ossService.isEnabled()) {
                    try {
                        String ossUrl = ossService.uploadFile(capturePath, "alarm/" + deviceId + "/" + 
                            new java.text.SimpleDateFormat("yyyyMMdd").format(new java.util.Date()) + 
                            "/" + new java.io.File(capturePath).getName());
                        if (ossUrl != null) {
                            logger.info("报警抓图已上传到OSS: deviceId={}, ossUrl={}", deviceId, ossUrl);
                        } else {
                            logger.warn("报警抓图上传OSS失败: deviceId={}", deviceId);
                        }
                    } catch (Exception e) {
                        logger.error("上传报警抓图到OSS异常: deviceId={}", deviceId, e);
                    }
                }
            } else {
                logger.error("报警自动抓图失败: deviceId={}, channel={}", deviceId, actualChannel);
            }
        } catch (Exception e) {
            logger.error("处理报警事件异常: deviceId={}, alarmType={}", deviceId, alarmType, e);
        }
    }
}
