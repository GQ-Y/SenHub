package com.digital.video.gateway.service;

import com.digital.video.gateway.device.DeviceManager;
import com.digital.video.gateway.device.DeviceSDK;
import com.digital.video.gateway.database.DeviceInfo;
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
     * 
     * @param deviceId 设备ID
     * @param channel  通道号
     * @param command  控制命令（up/down/left/right/zoom_in/zoom_out）
     * @param action   动作（start/stop）
     * @param speed    速度（1-7）
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

            // 验证并调整速度范围
            String brand = device.getBrand() != null ? device.getBrand().toLowerCase() : "auto";
            if ("tiandy".equals(brand)) {
                // 天地伟业速度范围 0-100
                if (speed < 0)
                    speed = 0;
                if (speed > 100)
                    speed = 100;
            } else if ("hikvision".equals(brand)) {
                // 海康速度范围 1-7
                if (speed < 1)
                    speed = 1;
                if (speed > 7)
                    speed = 7;
            } else if ("dahua".equals(brand)) {
                // 大华通常也是 1-8 或类似，这里暂时映射到合理范围
                if (speed < 1)
                    speed = 1;
                if (speed > 8)
                    speed = 8;
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
     * 云台绝对定位
     * 
     * @param deviceId 设备ID
     * @param channel  通道号
     * @param pan      水平角度
     * @param tilt     垂直角度
     * @param zoom     变倍
     * @return 是否成功
     */
    public boolean gotoAngle(String deviceId, int channel, float pan, float tilt, float zoom) {
        DeviceInfo device = deviceManager.getDevice(deviceId);
        if (device == null) {
            logger.warn("设备不存在: {}", deviceId);
            return false;
        }

        // 确保设备已登录
        if (!deviceManager.isDeviceLoggedIn(deviceId)) {
            if (!deviceManager.loginDevice(device)) {
                logger.warn("设备未登录，无法定位云台: {}", deviceId);
                return false;
            }
        }

        DeviceSDK sdk = deviceManager.getDeviceSDK(deviceId);
        if (sdk == null) {
            return false;
        }

        int userId = deviceManager.getDeviceUserId(deviceId);
        int actualChannel = channel > 0 ? channel : device.getChannel();

        try {
            boolean result = sdk.gotoAngle(userId, actualChannel, pan, tilt, zoom);
            if (result) {
                logger.debug("云台绝对定位成功: deviceId={}, pan={}, tilt={}", deviceId, pan, tilt);
            }
            return result;
        } catch (Exception e) {
            logger.error("云台绝对定位异常: deviceId={}", deviceId, e);
            return false;
        }
    }

    /**
     * 云台转到预置点
     *
     * @param deviceId    设备ID
     * @param channel     通道号
     * @param presetIndex 预置点号（1-based）
     * @return 是否成功
     */
    public boolean gotoPreset(String deviceId, int channel, int presetIndex) {
        DeviceInfo device = deviceManager.getDevice(deviceId);
        if (device == null) {
            logger.warn("设备不存在: {}", deviceId);
            return false;
        }
        if (!deviceManager.isDeviceLoggedIn(deviceId)) {
            if (!deviceManager.loginDevice(device)) {
                logger.warn("设备未登录，无法转预置点: {}", deviceId);
                return false;
            }
        }
        DeviceSDK sdk = deviceManager.getDeviceSDK(deviceId);
        if (sdk == null) {
            return false;
        }
        int userId = deviceManager.getDeviceUserId(deviceId);
        int actualChannel = channel > 0 ? channel : device.getChannel();
        try {
            boolean result = sdk.gotoPreset(userId, actualChannel, presetIndex);
            if (result) {
                logger.debug("云台转预置点成功: deviceId={}, presetIndex={}", deviceId, presetIndex);
            }
            return result;
        } catch (Exception e) {
            logger.error("云台转预置点异常: deviceId={}", deviceId, e);
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
