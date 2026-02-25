package com.digital.video.gateway.device;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.digital.video.gateway.config.Config;
import com.digital.video.gateway.database.Database;
import com.digital.video.gateway.database.DeviceInfo;
import com.digital.video.gateway.mqtt.MqttPublisher;
import com.digital.video.gateway.tiandy.TiandySDK;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 设备管理器
 * 管理设备的登录状态、设备信息等
 * 支持多品牌SDK（海康、天地伟业、大华）
 */
public class DeviceManager {
    private static final Logger logger = LoggerFactory.getLogger(DeviceManager.class);
    private Database database;
    private Config.DeviceConfig config;
    private MqttPublisher mqttPublisher;
    private ObjectMapper objectMapper = new ObjectMapper();

    // 设备登录状态映射：deviceId -> userId
    private final Map<String, Integer> deviceLoginMap = new ConcurrentHashMap<>();
    // 设备SDK映射：deviceId -> SDK实例
    private final Map<String, DeviceSDK> deviceSDKMap = new ConcurrentHashMap<>();
    // 设备登录锁：deviceId -> 锁对象，防止同一设备并发登录
    private final Map<String, Object> deviceLoginLocks = new ConcurrentHashMap<>();
    /** 天地伟业预览连接复用：deviceId -> 预览 connectID，登录成功后启动、登出时停止 */
    private final Map<String, Integer> tiandyPreviewConnectMap = new ConcurrentHashMap<>();
    /** 设备健康状态（天地伟业：仅 HEALTHY 时处理事件/抓图） */
    private final Map<String, DeviceInfo.HealthStatus> deviceHealthStatusMap = new ConcurrentHashMap<>();
    /** 设备运行时状态（用于观察登录/预览/下载等阶段） */
    private final Map<String, RuntimeState> deviceRuntimeStateMap = new ConcurrentHashMap<>();
    /** 天地伟业预览启动线程池，避免登录风暴时一设备一线程 */
    private final ExecutorService tiandyPreviewStarterExecutor = Executors.newFixedThreadPool(8, r -> {
        Thread t = new Thread(r, "TiandyPreviewStarter");
        t.setDaemon(true);
        return t;
    });

    public enum RuntimeState {
        OFFLINE,
        LOGGED_IN,
        PREVIEWING,
        DEGRADED
    }

    public DeviceManager(Database database, Config.DeviceConfig config) {
        this.database = database;
        this.config = config;
    }

    /**
     * 登录设备（线程安全，防止同一设备并发登录）
     */
    public boolean loginDevice(DeviceInfo device) {
        return loginDevice(device, true);
    }

    /**
     * 登录设备（仅验证，不保存到数据库）
     * 用于手动扫描时验证设备是否可登录
     */
    public boolean loginDeviceWithoutSave(DeviceInfo device) {
        return loginDevice(device, false);
    }

    /**
     * 登录设备（线程安全，防止同一设备并发登录）
     * 
     * @param device         设备信息
     * @param saveToDatabase 是否保存到数据库
     */
    private boolean loginDevice(DeviceInfo device, boolean saveToDatabase) {
        String deviceId = device.getDeviceId();

        // 获取或创建该设备的登录锁
        Object lock = deviceLoginLocks.computeIfAbsent(deviceId, k -> new Object());

        // 同步块，确保同一设备不会并发登录
        synchronized (lock) {
            // 再次检查是否已登录（双重检查锁定模式）
            // 注意：天地伟业SDK登录成功后返回的logonID可以是0，所以这里检查 >= 0
            if (deviceLoginMap.containsKey(deviceId)) {
                int userId = deviceLoginMap.get(deviceId);
                if (userId >= 0) {
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
                    device.setStatus(0);
                    if (saveToDatabase) {
                        database.updateDeviceStatus(deviceId, 0, -1);
                    }
                    return false;
                }
                logger.debug("使用指定品牌SDK登录: {} (品牌: {})", deviceId, brand);
                // 天地伟业SDK需要使用int类型端口（默认常见为3000，也支持自定义端口）
                // 因为其结构体中端口是int类型，而不是short类型
                if ("tiandy".equals(brand)) {
                    TiandySDK tiandySDK = (TiandySDK) sdk;
                    userId = tiandySDK.loginWithIntPort(device.getIp(), port, username, password);
                } else {
                    userId = sdk.login(device.getIp(), (short) port, username, password);
                }
            } else {
                // 只有品牌为auto时才进行自动检测
                logger.debug("品牌为auto，开始自动检测设备品牌: {}", deviceId);

                // 如果还没有成功，尝试标准检测流程
                if (userId == -1) {
                    // 1) 先走SDKFactory统一探测流程（支持int端口）
                    SDKFactory.BrandDetectionResult result = SDKFactory.detectBrand(
                            device.getIp(), port, username, password);
                    if (result != null) {
                        sdk = result.getSdk();
                        userId = result.getUserId();
                        detectedBrand = result.getBrand();
                        // 保存检测到的品牌
                        device.setBrand(detectedBrand);
                        logger.info("自动检测到设备品牌: {} (品牌: {})", deviceId, detectedBrand);
                    }

                    // 2) 高端口场景兜底：37777 更常见于大华，优先尝试大华
                    if (userId == -1 && port > 32767) {
                        DeviceSDK dahuaSDK = SDKFactory.getSDK("dahua");
                        if (dahuaSDK != null) {
                            int dahuaUserId = dahuaSDK.login(device.getIp(), (short) port, username, password);
                            if (dahuaUserId != -1) {
                                sdk = dahuaSDK;
                                userId = dahuaUserId;
                                detectedBrand = "dahua";
                                device.setBrand(detectedBrand);
                                logger.info("自动检测到设备品牌: {} (品牌: dahua, 端口: {})", deviceId, port);
                            }
                        }
                    }

                    // 3) 天地伟业兜底：使用 int 端口尝试，避免 short 端口截断问题
                    if (userId == -1) {
                        DeviceSDK tiandySDK = SDKFactory.getSDK("tiandy");
                        if (tiandySDK instanceof TiandySDK) {
                            int tiandyUserId = ((TiandySDK) tiandySDK).loginWithIntPort(
                                    device.getIp(), port, username, password);
                            if (tiandyUserId != -1) {
                                sdk = tiandySDK;
                                userId = tiandyUserId;
                                detectedBrand = "tiandy";
                                device.setBrand(detectedBrand);
                                logger.info("自动检测到设备品牌: {} (品牌: tiandy, 端口: {})", deviceId, port);
                            }
                        }
                    }

                    // 如果所有尝试都失败
                    if (userId == -1) {
                        device.setBrand(DeviceInfo.BRAND_UNKNOWN);
                        device.setStatus(0);
                        if (saveToDatabase) {
                            database.updateDeviceStatus(deviceId, 0, -1);
                        }
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
                deviceRuntimeStateMap.put(deviceId, RuntimeState.LOGGED_IN);
                device.setUserId(userId);
                device.setStatus(1);
                device.setBrand(detectedBrand);

                // 获取并保存通道号（从SDK登录返回的设备信息中获取）
                if (device.getChannel() <= 0) {
                    device.setChannel(1); // 默认通道1
                }

                if (saveToDatabase) {
                    database.updateDeviceStatus(deviceId, 1, userId);
                    database.saveOrUpdateDevice(device); // 保存设备信息包括品牌和通道号
                }

                // 天地伟业：登录成功后立即启动预览
                if ("tiandy".equalsIgnoreCase(detectedBrand) && sdk instanceof TiandySDK) {
                    final int channel = device.getChannel() > 0 ? device.getChannel() : 1;
                    final String finalDeviceId = deviceId;
                    final int finalUserId = userId;
                    final TiandySDK finalSdk = (TiandySDK) sdk;

                    tiandyPreviewStarterExecutor.submit(() -> {
                        try {
                            logger.info("天地伟业设备登录成功,启动预览: deviceId={}, userId={}", finalDeviceId, finalUserId);

                            if (!isDeviceLoggedIn(finalDeviceId)) {
                                logger.warn("天地伟业设备已离线,取消预览启动: deviceId={}", finalDeviceId);
                                return;
                            }

                            int connectId = finalSdk.startRealPlay(finalUserId, channel, 0);
                            if (connectId >= 0) {
                                tiandyPreviewConnectMap.put(finalDeviceId, connectId);
                                deviceHealthStatusMap.put(finalDeviceId, DeviceInfo.HealthStatus.HEALTHY);
                                deviceRuntimeStateMap.put(finalDeviceId, RuntimeState.PREVIEWING);
                                logger.info("天地伟业预览已启动: deviceId={}, connectId={}, channel={}", finalDeviceId,
                                        connectId, channel);
                            } else {
                                deviceHealthStatusMap.put(finalDeviceId, DeviceInfo.HealthStatus.PREVIEW_UNAVAILABLE);
                                deviceRuntimeStateMap.put(finalDeviceId, RuntimeState.DEGRADED);
                                logger.warn("天地伟业预览启动失败: deviceId={}, userId={}", finalDeviceId, finalUserId);
                            }
                        } catch (Exception e) {
                            deviceRuntimeStateMap.put(finalDeviceId, RuntimeState.DEGRADED);
                            logger.error("天地伟业预览启动异常: deviceId={}", finalDeviceId, e);
                        }
                    });

                    // 登录时先标记为预览不可用,等待延迟启动完成
                    deviceHealthStatusMap.put(deviceId, DeviceInfo.HealthStatus.PREVIEW_UNAVAILABLE);
                }

                logger.info("设备登录成功: {} (品牌: {}, userId: {}, channel: {}{})",
                        deviceId, detectedBrand, userId, device.getChannel(), saveToDatabase ? "" : ", 未保存到数据库");
                return true;
            } else {
                device.setStatus(0);
                if (saveToDatabase) {
                    database.updateDeviceStatus(deviceId, 0, -1);
                }
                String errorMsg = sdk != null ? sdk.getLastErrorString() : "SDK未初始化";
                logger.warn("设备登录失败: {} (品牌: {}, 错误: {})", deviceId, brand, errorMsg);
                logger.warn("登录参数: IP={}, Port={}, Username={}", device.getIp(), port, username);
                return false;
            }
        } // 同步块结束
    }

    /**
     * 登出设备
     */
    public boolean logoutDevice(String deviceId) {
        Integer userId = deviceLoginMap.get(deviceId);
        // 注意：天地伟业 SDK 登录成功可能返回 0，只有 -1 或 null 表示未登录
        if (userId == null || userId < 0) {
            logger.debug("设备未登录: {}", deviceId);
            return false;
        }

        DeviceSDK sdk = deviceSDKMap.get(deviceId);
        if (sdk == null) {
            logger.warn("设备SDK不存在: {}", deviceId);
            deviceLoginMap.remove(deviceId);
            return false;
        }

        // 天地伟业：登出前先停止预览并清除复用连接
        if ("tiandy".equalsIgnoreCase(sdk.getBrand()) && sdk instanceof TiandySDK) {
            Integer previewConnectId = tiandyPreviewConnectMap.remove(deviceId);
            if (previewConnectId != null) {
                ((TiandySDK) sdk).stopRealPlay(previewConnectId);
                logger.info("天地伟业预览已停止: deviceId={}, connectId={}", deviceId, previewConnectId);
            }
            deviceHealthStatusMap.remove(deviceId);
            deviceRuntimeStateMap.put(deviceId, RuntimeState.OFFLINE);
        }

        boolean result = sdk.logout(userId);
        // 无论 SDK 登出是否成功，都清除本地登录状态，避免“登出后仍显示已登录”
        deviceLoginMap.remove(deviceId);
        deviceSDKMap.remove(deviceId);
        deviceRuntimeStateMap.put(deviceId, RuntimeState.OFFLINE);
        database.updateDeviceStatus(deviceId, 0, -1);
        if (result) {
            logger.info("设备登出成功: {}", deviceId);
        } else {
            logger.warn("设备登出 SDK 返回失败，已清除本地状态: {} (错误: {})", deviceId, sdk.getLastErrorString());
        }
        return result;
    }

    /**
     * 获取设备登录状态
     */
    public boolean isDeviceLoggedIn(String deviceId) {
        // 注意：天地伟业SDK登录成功后返回的logonID可以是0
        Integer userId = deviceLoginMap.get(deviceId);
        return userId != null && userId >= 0;
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
     * 获取天地伟业设备的预览连接ID（登录成功后已启动的预览，用于抓图复用）
     * 
     * @return 预览 connectID，无复用或非天地伟业时返回 -1
     */
    public int getTiandyPreviewConnectId(String deviceId) {
        return tiandyPreviewConnectMap.getOrDefault(deviceId, -1);
    }

    /**
     * 清除天地伟业设备的预览复用连接。
     * 仅用于登出等显式断开场景；抓图失败/超时不应调用，以保持预览长连供后续抓图使用。
     */
    public void clearTiandyPreview(String deviceId) {
        Integer previewConnectId = tiandyPreviewConnectMap.remove(deviceId);
        if (previewConnectId != null) {
            DeviceSDK sdk = deviceSDKMap.get(deviceId);
            if (sdk instanceof TiandySDK) {
                ((TiandySDK) sdk).stopRealPlay(previewConnectId);
                logger.info("天地伟业预览复用已清除(抓图失败或连接无效): deviceId={}, connectId={}", deviceId, previewConnectId);
            }
            deviceHealthStatusMap.put(deviceId, DeviceInfo.HealthStatus.PREVIEW_UNAVAILABLE);
            deviceRuntimeStateMap.put(deviceId, RuntimeState.DEGRADED);
        }
    }

    /**
     * 天地伟业设备健康检查：仅检查已有预览连接，不尝试建立新连接。
     * 报警回调线程中调用 SyncRealPlay 会导致 SDK 内部死锁，
     * 新连接的建立由 TiandyPreviewStarter 定时任务或 CaptureService 负责。
     */
    public DeviceInfo.HealthStatus checkTiandyDeviceHealth(String deviceId) {
        DeviceInfo device = getDevice(deviceId);
        if (device == null)
            return DeviceInfo.HealthStatus.UNKNOWN;
        if (!DeviceInfo.BRAND_TIANDY.equalsIgnoreCase(device.getBrand()))
            return DeviceInfo.HealthStatus.HEALTHY;
        int connectId = getTiandyPreviewConnectId(deviceId);
        if (connectId >= 0) {
            deviceHealthStatusMap.put(deviceId, DeviceInfo.HealthStatus.HEALTHY);
            deviceRuntimeStateMap.put(deviceId, RuntimeState.PREVIEWING);
            return DeviceInfo.HealthStatus.HEALTHY;
        }
        deviceHealthStatusMap.put(deviceId, DeviceInfo.HealthStatus.PREVIEW_UNAVAILABLE);
        deviceRuntimeStateMap.put(deviceId, RuntimeState.DEGRADED);
        return DeviceInfo.HealthStatus.PREVIEW_UNAVAILABLE;
    }

    /**
     * 获取设备当前健康状态（仅天地伟业使用；未检查或非天地伟业为 UNKNOWN/HEALTHY）
     */
    public DeviceInfo.HealthStatus getDeviceHealthStatus(String deviceId) {
        return deviceHealthStatusMap.getOrDefault(deviceId, DeviceInfo.HealthStatus.UNKNOWN);
    }

    /**
     * 确保天地伟业设备有预览连接；若已存在则返回 connectId，若无且设备已登录则尝试启动预览并返回新 connectId。
     * 用于抓图前“无预览连接”时尝试重建，避免因一次失败/清除后必须重启服务才能抓图。
     * 
     * @return 预览 connectID，无法建立时返回 -1
     */
    public int ensureTiandyPreview(String deviceId) {
        int existing = getTiandyPreviewConnectId(deviceId);
        if (existing >= 0)
            return existing;
        Integer userId = deviceLoginMap.get(deviceId);
        if (userId == null || userId < 0)
            return -1;
        DeviceSDK sdk = deviceSDKMap.get(deviceId);
        if (!(sdk instanceof TiandySDK))
            return -1;
        DeviceInfo device = getDevice(deviceId);
        if (device == null)
            return -1;
        int channel = device.getChannel() > 0 ? device.getChannel() : 1;
        int connectId = ((TiandySDK) sdk).startRealPlay(userId, channel, 0);
        if (connectId >= 0) {
            tiandyPreviewConnectMap.put(deviceId, connectId);
            deviceHealthStatusMap.put(deviceId, DeviceInfo.HealthStatus.HEALTHY);
            deviceRuntimeStateMap.put(deviceId, RuntimeState.PREVIEWING);
            logger.info("天地伟业预览已重新建立: deviceId={}, connectId={}, channel={}", deviceId, connectId, channel);
            return connectId;
        }
        deviceRuntimeStateMap.put(deviceId, RuntimeState.DEGRADED);
        return -1;
    }

    /** 获取设备运行时状态（未记录时返回 OFFLINE） */
    public RuntimeState getDeviceRuntimeState(String deviceId) {
        return deviceRuntimeStateMap.getOrDefault(deviceId, RuntimeState.OFFLINE);
    }

    /**
     * 通过登录句柄(userId/logonID)和品牌查找设备ID
     * 用于报警回调中根据SDK返回的句柄查找真实的设备ID
     * 
     * @param loginHandle SDK登录句柄
     * @param brand       设备品牌（用于区分不同SDK的句柄）
     * @return 设备ID，未找到返回null
     */
    public String getDeviceIdByLoginHandle(int loginHandle, String brand) {
        for (java.util.Map.Entry<String, Integer> entry : deviceLoginMap.entrySet()) {
            if (entry.getValue() != null && entry.getValue() == loginHandle) {
                String deviceId = entry.getKey();
                // 如果指定了品牌，需要验证品牌匹配
                if (brand != null) {
                    DeviceSDK sdk = deviceSDKMap.get(deviceId);
                    if (sdk != null && sdk.getBrand().equalsIgnoreCase(brand)) {
                        return deviceId;
                    }
                } else {
                    return deviceId;
                }
            }
        }
        return null;
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
    public void updateDeviceStatus(String deviceId, int status) {
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
    public boolean updateDeviceStatusFromCallback(int userId, int status) {
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

        int oldStatus = device.getStatus();
        if (status == oldStatus) {
            logger.debug("设备状态未变化，跳过更新: {} (状态: {})", deviceId, status);
            return false;
        }

        // 更新数据库状态
        updateDeviceStatus(deviceId, status);

        // 如果设备离线，从登录映射中移除，并停止天地伟业预览
        if (status == 0) {
            DeviceSDK sdk = deviceSDKMap.get(deviceId);
            if ("tiandy".equalsIgnoreCase(device.getBrand()) && sdk instanceof TiandySDK) {
                Integer previewConnectId = tiandyPreviewConnectMap.remove(deviceId);
                if (previewConnectId != null) {
                    ((TiandySDK) sdk).stopRealPlay(previewConnectId);
                    logger.info("设备离线，天地伟业预览已停止: deviceId={}, connectId={}", deviceId, previewConnectId);
                }
            }
            deviceLoginMap.remove(deviceId);
            deviceSDKMap.remove(deviceId);
            deviceRuntimeStateMap.put(deviceId, RuntimeState.OFFLINE);
            logger.info("设备离线，已从登录映射中移除: {} (userId: {})", deviceId, userId);
        } else if (status == 1) {
            deviceRuntimeStateMap.put(deviceId, RuntimeState.LOGGED_IN);
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
        tiandyPreviewStarterExecutor.shutdown();
        try {
            if (!tiandyPreviewStarterExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                tiandyPreviewStarterExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            tiandyPreviewStarterExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 设置MQTT发布器（用于状态通知；连接失败时自动降级）
     */
    public void setMqttPublisher(MqttPublisher mqttPublisher) {
        this.mqttPublisher = mqttPublisher;
        logger.debug("DeviceManager已设置MQTT发布器");
    }

    /**
     * 更新设备状态并触发MQTT通知（统一的状态更新和通知机制）
     */
    public void updateDeviceStatusWithNotification(String deviceId, int status) {
        DeviceInfo device = getDevice(deviceId);
        if (device == null) {
            logger.warn("尝试更新不存在的设备状态: {}", deviceId);
            return;
        }

        int oldStatus = device.getStatus();
        if (status != oldStatus) {
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
     * 发布设备状态到 MQTT（senhub/device/status，payload 含
     * entity_type=camera、device_id、type、device_info 含 camera_type）
     */
    private void publishDeviceStatus(DeviceInfo device, int status) {
        if (mqttPublisher == null) {
            logger.warn("MQTT发布器未设置，无法发布设备状态");
            return;
        }
        try {
            Map<String, Object> statusMessage = new HashMap<>();
            statusMessage.put("entity_type", "camera");
            statusMessage.put("device_id", device.getDeviceId());
            statusMessage.put("type", status == 1 ? "online" : "offline");
            statusMessage.put("timestamp", System.currentTimeMillis() / 1000);

            Map<String, Object> deviceInfoMap = new HashMap<>();
            deviceInfoMap.put("name", device.getName());
            deviceInfoMap.put("ip", device.getIp());
            deviceInfoMap.put("port", device.getPort());
            deviceInfoMap.put("rtsp_url", device.getRtspUrl());
            deviceInfoMap.put("brand", device.getBrand());
            deviceInfoMap.put("camera_type", device.getCameraType() != null ? device.getCameraType() : "other");
            if (device.getSerialNumber() != null && !device.getSerialNumber().isEmpty()) {
                deviceInfoMap.put("serial_number", device.getSerialNumber());
            }
            statusMessage.put("device_info", deviceInfoMap);
            if (status != 1)
                statusMessage.put("reason", "disconnected");

            String messageJson = objectMapper.writeValueAsString(statusMessage);
            mqttPublisher.publishStatus(messageJson);
            logger.debug("设备状态已发布到MQTT: {}", messageJson);
        } catch (Exception e) {
            logger.error("发布设备状态到MQTT失败: {}", device.getDeviceId(), e);
        }
    }
}
