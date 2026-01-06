package com.hikvision.nvr.device;

import com.hikvision.nvr.config.Config;
import com.hikvision.nvr.database.Database;
import com.hikvision.nvr.database.DeviceInfo;
import com.hikvision.nvr.hikvision.HikvisionSDK;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 设备管理器
 * 管理设备的登录状态、设备信息等
 */
public class DeviceManager {
    private static final Logger logger = LoggerFactory.getLogger(DeviceManager.class);
    private HikvisionSDK sdk;
    private Database database;
    private Config.DeviceConfig config;
    
    // 设备登录状态映射：deviceId -> userId
    private final Map<String, Integer> deviceLoginMap = new ConcurrentHashMap<>();

    public DeviceManager(HikvisionSDK sdk, Database database, Config.DeviceConfig config) {
        this.sdk = sdk;
        this.database = database;
        this.config = config;
    }

    /**
     * 登录设备
     */
    public boolean loginDevice(DeviceInfo device) {
        String deviceId = device.getDeviceId();
        
        // 检查是否已登录
        if (deviceLoginMap.containsKey(deviceId)) {
            int userId = deviceLoginMap.get(deviceId);
            if (userId > 0) {
                logger.debug("设备已登录: {} (userId: {})", deviceId, userId);
                return true;
            }
        }

        // 执行登录
        String username = device.getUsername() != null ? device.getUsername() : config.getDefaultUsername();
        String password = device.getPassword() != null ? device.getPassword() : config.getDefaultPassword();
        int port = device.getPort() > 0 ? device.getPort() : config.getDefaultPort();

                int userId = sdk.login(device.getIp(), (short) port, username, password);

                // 按照示例代码的逻辑：只要 userId != -1 就认为登录成功（包括 userId=0）
                // 示例代码中：if (userID == -1) 表示失败，else 表示成功
                if (userId != -1) {
                    deviceLoginMap.put(deviceId, userId);
                    device.setUserId(userId);
                    device.setStatus("online");
                    
                    // 获取并保存通道号（从SDK登录返回的设备信息中获取）
                    // 注意：这里需要从HikvisionSDK获取设备信息，暂时使用默认值1
                    // 实际应该从登录时返回的设备信息中获取byStartChan
                    if (device.getChannel() <= 0) {
                        device.setChannel(1); // 默认通道1
                    }
                    
                    database.updateDeviceStatus(deviceId, "online", userId);
                    database.saveOrUpdateDevice(device); // 保存设备信息包括通道号
                    logger.info("设备登录成功: {} (userId: {}, channel: {})", deviceId, userId, device.getChannel());
                    return true;
                } else {
            device.setStatus("offline");
            database.updateDeviceStatus(deviceId, "offline", -1);
            int errorCode = sdk.getLastError();
            logger.warn("设备登录失败: {} (错误码: {})", deviceId, errorCode);
            logger.warn("登录参数: IP={}, Port={}, Username={}", device.getIp(), port, username);
            return false;
        }
    }

    /**
     * 登出设备
     */
    public boolean logoutDevice(String deviceId) {
        Integer userId = deviceLoginMap.get(deviceId);
        if (userId == null || userId <= 0) {
            logger.debug("设备未登录: {}", deviceId);
            return false;
        }

        boolean result = sdk.logout(userId);
        if (result) {
            deviceLoginMap.remove(deviceId);
            database.updateDeviceStatus(deviceId, "offline", -1);
            logger.info("设备登出成功: {}", deviceId);
        } else {
            logger.error("设备登出失败: {}", deviceId);
        }
        return result;
    }

    /**
     * 获取设备登录状态
     */
    public boolean isDeviceLoggedIn(String deviceId) {
        Integer userId = deviceLoginMap.get(deviceId);
        return userId != null && userId > 0;
    }

    /**
     * 获取设备的用户ID
     */
    public int getDeviceUserId(String deviceId) {
        Integer userId = deviceLoginMap.get(deviceId);
        return userId != null ? userId : -1;
    }

    /**
     * 获取设备信息
     */
    public DeviceInfo getDevice(String deviceId) {
        return database.getDevice(deviceId);
    }

    /**
     * 获取所有设备
     */
    public List<DeviceInfo> getAllDevices() {
        return database.getAllDevices();
    }

    /**
     * 更新设备状态
     */
    public void updateDeviceStatus(String deviceId, String status) {
        int userId = getDeviceUserId(deviceId);
        database.updateDeviceStatus(deviceId, status, userId);
        
        DeviceInfo device = getDevice(deviceId);
        if (device != null) {
            device.setStatus(status);
        }
    }

    /**
     * 登出所有设备
     */
    public void logoutAll() {
        for (String deviceId : deviceLoginMap.keySet()) {
            logoutDevice(deviceId);
        }
    }
}
