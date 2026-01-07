package com.hikvision.nvr.service;

import com.hikvision.nvr.device.DeviceManager;
import com.hikvision.nvr.device.DeviceSDK;
import com.hikvision.nvr.database.DeviceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 云台控制服务
 * 根据设备品牌选择对应的SDK实现云台控制功能
 */
public class PTZService {
    private static final Logger logger = LoggerFactory.getLogger(PTZService.class);
    
    private final DeviceManager deviceManager;
    
    public PTZService(DeviceManager deviceManager) {
        this.deviceManager = deviceManager;
    }
    
    /**
     * 云台控制
     * @param deviceId 设备ID
     * @param channel 通道号
     * @param command 控制命令（up/down/left/right/zoom_in/zoom_out）
     * @param action 动作（start/stop）
     * @param speed 速度（1-7）
     * @return 是否成功
     */
    public boolean ptzControl(String deviceId, int channel, String command, String action, int speed) {
        DeviceInfo device = deviceManager.getDevice(deviceId);
        if (device == null) {
            logger.warn("设备不存在: {}", deviceId);
            return false;
        }
        
        // 确保设备已登录
        if (!deviceManager.isDeviceLoggedIn(deviceId)) {
            if (!deviceManager.loginDevice(device)) {
                logger.warn("设备未登录，无法控制云台: {}", deviceId);
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
        int actualChannel = channel > 0 ? channel : device.getChannel();
        
        try {
            // 验证命令
            if (!isValidCommand(command)) {
                logger.error("无效的云台控制命令: {}", command);
                return false;
            }
            
            // 验证动作
            if (!"start".equalsIgnoreCase(action) && !"stop".equalsIgnoreCase(action)) {
                logger.error("无效的云台控制动作: {}", action);
                return false;
            }
            
            // 验证速度
            if (speed < 1 || speed > 7) {
                logger.warn("云台控制速度超出范围，使用默认值: speed={}", speed);
                speed = 4; // 默认速度
            }
            
            // 执行云台控制
            boolean result = sdk.ptzControl(userId, actualChannel, command, action, speed);
            if (result) {
                logger.info("云台控制成功: deviceId={}, channel={}, command={}, action={}, speed={}", 
                    deviceId, actualChannel, command, action, speed);
                return true;
            } else {
                logger.error("云台控制失败: deviceId={}, channel={}, command={}", 
                    deviceId, actualChannel, command);
                return false;
            }
        } catch (Exception e) {
            logger.error("云台控制异常: deviceId={}, channel={}, command={}", deviceId, channel, command, e);
            return false;
        }
    }
    
    /**
     * 验证命令是否有效
     */
    private boolean isValidCommand(String command) {
        if (command == null) {
            return false;
        }
        String cmd = command.toLowerCase();
        return "up".equals(cmd) || "down".equals(cmd) || 
               "left".equals(cmd) || "right".equals(cmd) || 
               "zoom_in".equals(cmd) || "zoom_out".equals(cmd);
    }
}
