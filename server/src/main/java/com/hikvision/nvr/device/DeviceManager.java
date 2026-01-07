package com.hikvision.nvr.device;

import com.hikvision.nvr.config.Config;
import com.hikvision.nvr.database.Database;
import com.hikvision.nvr.database.DeviceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 设备管理器
 * 管理设备的登录状态、设备信息等
 * 支持多品牌SDK（海康、天地伟业、大华）
 */
public class DeviceManager {
    private static final Logger logger = LoggerFactory.getLogger(DeviceManager.class);
    private Database database;
    private Config.DeviceConfig config;
    
    // 设备登录状态映射：deviceId -> userId
    private final Map<String, Integer> deviceLoginMap = new ConcurrentHashMap<>();
    // 设备SDK映射：deviceId -> SDK实例
    private final Map<String, DeviceSDK> deviceSDKMap = new ConcurrentHashMap<>();

    public DeviceManager(Database database, Config.DeviceConfig config) {
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
        
        // 获取设备品牌
        String brand = device.getBrand();
        if (brand == null || brand.isEmpty() || DeviceInfo.BRAND_AUTO.equals(brand)) {
            brand = DeviceInfo.BRAND_AUTO;
        }
        
        DeviceSDK sdk = null;
        int userId = -1;
        String detectedBrand = brand;
        
        // 如果品牌是auto，进行自动检测
        if (DeviceInfo.BRAND_AUTO.equals(brand)) {
            SDKFactory.BrandDetectionResult result = SDKFactory.detectBrand(
                device.getIp(), (short)port, username, password);
            if (result != null) {
                sdk = result.getSdk();
                userId = result.getUserId();
                detectedBrand = result.getBrand();
                // 保存检测到的品牌
                device.setBrand(detectedBrand);
            } else {
                // 所有SDK都失败
                device.setBrand(DeviceInfo.BRAND_UNKNOWN);
                device.setStatus("offline");
                database.updateDeviceStatus(deviceId, "offline", -1);
                logger.warn("设备登录失败: {} (所有SDK都失败)", deviceId);
                logger.warn("登录参数: IP={}, Port={}, Username={}", device.getIp(), port, username);
                return false;
            }
        } else {
            // 使用指定的品牌SDK
            sdk = SDKFactory.getSDK(brand);
            if (sdk == null) {
                logger.error("无法获取品牌SDK: {} (设备: {})", brand, deviceId);
                device.setStatus("offline");
                database.updateDeviceStatus(deviceId, "offline", -1);
                return false;
            }
            userId = sdk.login(device.getIp(), (short)port, username, password);
        }
        
        // 检查登录结果
        if (userId != -1) {
            deviceLoginMap.put(deviceId, userId);
            deviceSDKMap.put(deviceId, sdk);
            device.setUserId(userId);
            device.setStatus("online");
            device.setBrand(detectedBrand);
            
            // 获取并保存通道号（从SDK登录返回的设备信息中获取）
            if (device.getChannel() <= 0) {
                device.setChannel(1); // 默认通道1
            }
            
            database.updateDeviceStatus(deviceId, "online", userId);
            database.saveOrUpdateDevice(device); // 保存设备信息包括品牌和通道号
            logger.info("设备登录成功: {} (品牌: {}, userId: {}, channel: {})", 
                deviceId, detectedBrand, userId, device.getChannel());
            return true;
        } else {
            device.setStatus("offline");
            database.updateDeviceStatus(deviceId, "offline", -1);
            String errorMsg = sdk != null ? sdk.getLastErrorString() : "SDK未初始化";
            logger.warn("设备登录失败: {} (品牌: {}, 错误: {})", deviceId, brand, errorMsg);
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
        
        DeviceSDK sdk = deviceSDKMap.get(deviceId);
        if (sdk == null) {
            logger.warn("设备SDK不存在: {}", deviceId);
            deviceLoginMap.remove(deviceId);
            return false;
        }

        boolean result = sdk.logout(userId);
        if (result) {
            deviceLoginMap.remove(deviceId);
            deviceSDKMap.remove(deviceId);
            database.updateDeviceStatus(deviceId, "offline", -1);
            logger.info("设备登出成功: {}", deviceId);
        } else {
            logger.error("设备登出失败: {} (错误: {})", deviceId, sdk.getLastErrorString());
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
