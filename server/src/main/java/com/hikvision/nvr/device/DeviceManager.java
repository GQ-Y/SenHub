package com.hikvision.nvr.device;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hikvision.nvr.config.Config;
import com.hikvision.nvr.database.Database;
import com.hikvision.nvr.database.DeviceInfo;
import com.hikvision.nvr.mqtt.MqttClient;
import com.hikvision.nvr.tiandy.TiandySDK;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
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
    private MqttClient mqttClient;
    private ObjectMapper objectMapper = new ObjectMapper();
    
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
        if (brand == null || brand.isEmpty()) {
            brand = DeviceInfo.BRAND_AUTO;
        }
        
        DeviceSDK sdk = null;
        int userId = -1;
        String detectedBrand = brand;
        
        // 如果品牌已指定且不是auto，直接使用对应品牌的SDK，不再循环尝试
        if (!DeviceInfo.BRAND_AUTO.equals(brand) && !brand.isEmpty()) {
            // 使用指定的品牌SDK
            sdk = SDKFactory.getSDK(brand);
            if (sdk == null) {
                logger.error("无法获取品牌SDK: {} (设备: {})，SDK可能未初始化", brand, deviceId);
                device.setStatus("offline");
                database.updateDeviceStatus(deviceId, "offline", -1);
                return false;
            }
            logger.debug("使用指定品牌SDK登录: {} (品牌: {})", deviceId, brand);
            // 天地伟业SDK需要使用int类型端口（支持所有端口，包括3000和37777）
            // 因为其结构体中端口是int类型，而不是short类型
            if ("tiandy".equals(brand)) {
                TiandySDK tiandySDK = (TiandySDK) sdk;
                userId = tiandySDK.loginWithIntPort(device.getIp(), port, username, password);
            } else {
                userId = sdk.login(device.getIp(), (short)port, username, password);
            }
        } else {
            // 只有品牌为auto时才进行自动检测
            logger.debug("品牌为auto，开始自动检测设备品牌: {}", deviceId);
            // 如果端口大于32767，优先尝试天地伟业SDK（因为天地伟业默认端口37777）
            if (port > 32767) {
                DeviceSDK tiandySDK = SDKFactory.getSDK("tiandy");
                if (tiandySDK != null && tiandySDK instanceof TiandySDK) {
                    logger.debug("端口大于32767，优先尝试天地伟业SDK");
                    int tiandyUserId = ((TiandySDK) tiandySDK).loginWithIntPort(device.getIp(), port, username, password);
                    if (tiandyUserId != -1) {
                        sdk = tiandySDK;
                        userId = tiandyUserId;
                        detectedBrand = "tiandy";
                        device.setBrand(detectedBrand);
                        logger.info("自动检测到设备品牌: {} (品牌: tiandy, 端口: {})", deviceId, port);
                    } else {
                        // 天地伟业失败，继续尝试其他SDK
                        logger.debug("天地伟业SDK登录失败，继续尝试其他SDK");
                    }
                }
            }
            
            // 如果还没有成功，尝试标准检测流程
            if (userId == -1) {
                // 对于端口大于32767的情况，优先尝试天地伟业SDK
                if (port > 32767) {
                    DeviceSDK tiandySDK = SDKFactory.getSDK("tiandy");
                    if (tiandySDK != null && tiandySDK instanceof TiandySDK) {
                        int tiandyUserId = ((TiandySDK) tiandySDK).loginWithIntPort(device.getIp(), port, username, password);
                        if (tiandyUserId != -1) {
                            sdk = tiandySDK;
                            userId = tiandyUserId;
                            detectedBrand = "tiandy";
                            device.setBrand(detectedBrand);
                            logger.info("自动检测到设备品牌: {} (品牌: tiandy, 端口: {})", deviceId, port);
                        }
                    }
                }
                
                // 如果还没有成功，尝试标准检测流程（使用short端口）
                if (userId == -1 && port <= 32767) {
                    SDKFactory.BrandDetectionResult result = SDKFactory.detectBrand(
                        device.getIp(), (short)port, username, password);
                    if (result != null) {
                        sdk = result.getSdk();
                        userId = result.getUserId();
                        detectedBrand = result.getBrand();
                        // 保存检测到的品牌
                        device.setBrand(detectedBrand);
                        logger.info("自动检测到设备品牌: {} (品牌: {})", deviceId, detectedBrand);
                    }
                }
                
                // 如果所有尝试都失败
                if (userId == -1) {
                    device.setBrand(DeviceInfo.BRAND_UNKNOWN);
                    device.setStatus("offline");
                    database.updateDeviceStatus(deviceId, "offline", -1);
                    logger.warn("设备登录失败: {} (所有SDK都失败)", deviceId);
                    logger.warn("登录参数: IP={}, Port={}, Username={}", device.getIp(), port, username);
                    return false;
                }
            }
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
     * 获取设备SDK实例
     */
    public DeviceSDK getDeviceSDK(String deviceId) {
        return deviceSDKMap.get(deviceId);
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
     * 通过userId查找deviceId
     */
    public String getDeviceIdByUserId(int userId) {
        for (Map.Entry<String, Integer> entry : deviceLoginMap.entrySet()) {
            if (entry.getValue() != null && entry.getValue() == userId) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * 更新设备状态并触发通知（用于SDK回调）
     * 注意：此方法不发送MQTT通知，MQTT通知由调用者负责
     */
    public boolean updateDeviceStatusFromCallback(int userId, String status) {
        String deviceId = getDeviceIdByUserId(userId);
        if (deviceId == null) {
            logger.debug("无法通过userId找到deviceId: {}", userId);
            return false;
        }
        
        DeviceInfo device = getDevice(deviceId);
        if (device == null) {
            logger.warn("设备不存在: {}", deviceId);
            return false;
        }
        
        String oldStatus = device.getStatus();
        if (status.equals(oldStatus)) {
            logger.debug("设备状态未变化，跳过更新: {} (状态: {})", deviceId, status);
            return false;
        }
        
        // 更新数据库状态
        updateDeviceStatus(deviceId, status);
        
        // 如果设备离线，从登录映射中移除
        if ("offline".equals(status)) {
            deviceLoginMap.remove(deviceId);
            deviceSDKMap.remove(deviceId);
            logger.info("设备离线，已从登录映射中移除: {} (userId: {})", deviceId, userId);
        }
        
        logger.info("设备状态已更新: {} ({} -> {})", deviceId, oldStatus, status);
        return true;
    }

    /**
     * 登出所有设备
     */
    public void logoutAll() {
        for (String deviceId : deviceLoginMap.keySet()) {
            logoutDevice(deviceId);
        }
    }

    /**
     * 设置MQTT客户端（用于状态通知）
     */
    public void setMqttClient(MqttClient mqttClient) {
        this.mqttClient = mqttClient;
        logger.debug("DeviceManager已设置MQTT客户端");
    }

    /**
     * 更新设备状态并触发MQTT通知（统一的状态更新和通知机制）
     */
    public void updateDeviceStatusWithNotification(String deviceId, String status) {
        DeviceInfo device = getDevice(deviceId);
        if (device == null) {
            logger.warn("尝试更新不存在的设备状态: {}", deviceId);
            return;
        }

        String oldStatus = device.getStatus();
        if (!status.equals(oldStatus)) {
            // 更新数据库
            int userId = getDeviceUserId(deviceId);
            database.updateDeviceStatus(deviceId, status, userId);
            device.setStatus(status); // 更新内存中的设备对象状态
            logger.info("设备状态更新: {} -> {} (设备: {})", oldStatus, status, deviceId);

            // 发送MQTT通知
            publishDeviceStatus(device, status);
        }
    }

    /**
     * 发布设备状态到MQTT
     */
    private void publishDeviceStatus(DeviceInfo device, String status) {
        if (mqttClient == null) {
            logger.warn("MQTT客户端未设置，无法发布设备状态");
            return;
        }
        try {
            Map<String, Object> statusMessage = new HashMap<>();
            statusMessage.put("device_id", device.getDeviceId());
            statusMessage.put("status", status);
            statusMessage.put("timestamp", System.currentTimeMillis() / 1000);

            Map<String, Object> deviceInfoMap = new HashMap<>();
            deviceInfoMap.put("name", device.getName());
            deviceInfoMap.put("ip", device.getIp());
            deviceInfoMap.put("port", device.getPort());
            deviceInfoMap.put("rtsp_url", device.getRtspUrl());
            deviceInfoMap.put("brand", device.getBrand());
            statusMessage.put("device_info", deviceInfoMap);

            String messageJson = objectMapper.writeValueAsString(statusMessage);
            mqttClient.publishStatus(messageJson);
            logger.debug("设备状态已发布到MQTT: {}", messageJson);
        } catch (Exception e) {
            logger.error("发布设备状态到MQTT失败: {}", device.getDeviceId(), e);
        }
    }
}
