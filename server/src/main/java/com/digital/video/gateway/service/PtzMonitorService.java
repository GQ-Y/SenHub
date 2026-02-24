package com.digital.video.gateway.service;

import com.digital.video.gateway.config.Config;
import com.digital.video.gateway.database.Database;
import com.digital.video.gateway.database.DeviceInfo;
import com.digital.video.gateway.database.DevicePtzExtensionTable;
import com.digital.video.gateway.device.DeviceManager;
import com.digital.video.gateway.device.DeviceSDK;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * PTZ监控服务
 * 定时获取启用监控的球机PTZ位置信息，并更新到数据库
 */
public class PtzMonitorService {
    private static final Logger logger = LoggerFactory.getLogger(PtzMonitorService.class);

    private final Database database;
    private final DeviceManager deviceManager;
    private final Config config;
    
    private ScheduledExecutorService scheduler;
    private boolean running = false;

    public PtzMonitorService(Database database, DeviceManager deviceManager, Config config) {
        this.database = database;
        this.deviceManager = deviceManager;
        this.config = config;
    }

    /**
     * 启动PTZ监控服务
     */
    public void start() {
        if (running) {
            logger.warn("PTZ监控服务已经在运行中");
            return;
        }

        // 获取配置
        boolean enabled = config.getPtzMonitorEnabled();
        int intervalMs = config.getPtzMonitorInterval();

        if (!enabled) {
            logger.info("PTZ监控服务已禁用，跳过启动");
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PtzMonitorService");
            t.setDaemon(true);
            return t;
        });

        // 延迟5秒后开始，然后按照配置的间隔执行
        scheduler.scheduleAtFixedRate(this::pollPtzPositions, 5000, intervalMs, TimeUnit.MILLISECONDS);

        running = true;
        logger.info("PTZ监控服务已启动，间隔: {}ms", intervalMs);
    }

    /**
     * 停止PTZ监控服务
     */
    public void stop() {
        if (!running) {
            return;
        }

        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        running = false;
        logger.info("PTZ监控服务已停止");
    }

    /**
     * 轮询所有启用PTZ监控的设备，获取PTZ位置
     * 仅轮询 devices 表中存在的设备；对 PTZ 扩展表中存在但设备已不存在的脏数据自动关闭监控并只打一次日志。
     */
    private void pollPtzPositions() {
        try {
            List<DevicePtzExtensionTable.PtzExtension> enabledDevices = database.getAllEnabledPtzDevices();

            if (enabledDevices.isEmpty()) {
                logger.trace("没有启用PTZ监控的设备");
                return;
            }

            for (DevicePtzExtensionTable.PtzExtension ptzExt : enabledDevices) {
                String deviceId = ptzExt.getDeviceId();
                if (deviceManager.getDevice(deviceId) == null) {
                    // 设备表中不存在该 device_id，属脏数据，关闭其 PTZ 监控并只打一次日志
                    database.setPtzMonitorEnabled(deviceId, false);
                    logger.info("PTZ扩展表存在无效设备ID（设备表中无此设备），已自动关闭其PTZ监控: {}", deviceId);
                    continue;
                }
                try {
                    refreshPtzPosition(deviceId);
                } catch (Exception e) {
                    logger.error("获取设备PTZ位置失败: {}", deviceId, e);
                }
            }
        } catch (Exception e) {
            logger.error("PTZ轮询异常", e);
        }
    }

    /**
     * 刷新单个设备的PTZ位置
     * 
     * @param deviceId 设备ID
     * @return 是否成功获取
     */
    public boolean refreshPtzPosition(String deviceId) {
        DeviceInfo device = deviceManager.getDevice(deviceId);
        if (device == null) {
            logger.debug("设备不存在，跳过PTZ刷新: {}", deviceId);
            return false;
        }

        // 检查设备是否在线
        if (!deviceManager.isDeviceLoggedIn(deviceId)) {
            logger.debug("设备未登录，跳过PTZ位置获取: {}", deviceId);
            return false;
        }

        // 获取设备SDK
        DeviceSDK sdk = deviceManager.getDeviceSDK(deviceId);
        if (sdk == null) {
            logger.debug("无法获取设备SDK: {}", deviceId);
            return false;
        }

        int userId = deviceManager.getDeviceUserId(deviceId);
        int channel = device.getChannel() > 0 ? device.getChannel() : 1;

        try {
            // 调用SDK获取PTZ位置
            DeviceSDK.PtzPosition position = sdk.getPtzPosition(userId, channel);
            
            if (position == null) {
                logger.debug("设备不支持PTZ位置获取或获取失败: {}", deviceId);
                return false;
            }

            // 更新数据库
            // 注意：这里只更新pan, tilt, zoom，其他字段（azimuth, fov等）需要从GIS回调更新
            DevicePtzExtensionTable.PtzExtension ptzExt = database.getPtzExtension(deviceId);
            if (ptzExt == null) {
                // 如果记录不存在，创建新记录
                ptzExt = new DevicePtzExtensionTable.PtzExtension(deviceId);
                ptzExt.setPtzEnabled(true);
            }
            
            ptzExt.setPan(position.getPan());
            ptzExt.setTilt(position.getTilt());
            ptzExt.setZoom(position.getZoom());
            
            boolean saved = database.savePtzExtension(ptzExt);
            
            if (saved) {
                logger.debug("PTZ位置已更新: {} -> {}", deviceId, position);
            }
            
            return saved;
        } catch (Exception e) {
            logger.error("刷新PTZ位置异常: {}", deviceId, e);
            return false;
        }
    }

    /**
     * 获取设备的PTZ位置（从数据库读取缓存）
     * 
     * @param deviceId 设备ID
     * @return PTZ扩展信息，不存在返回null
     */
    public DevicePtzExtensionTable.PtzExtension getPtzPosition(String deviceId) {
        return database.getPtzExtension(deviceId);
    }

    /**
     * 设置设备PTZ监控开关
     * 
     * @param deviceId 设备ID
     * @param enabled 是否启用
     * @return 是否成功
     */
    public boolean setPtzMonitorEnabled(String deviceId, boolean enabled) {
        return database.setPtzMonitorEnabled(deviceId, enabled);
    }

    /**
     * 检查服务是否正在运行
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * 更新PTZ扩展信息（用于GIS回调等外部更新）
     * 
     * @param deviceId 设备ID
     * @param pan 水平角度
     * @param tilt 垂直角度
     * @param zoom 变倍
     * @param azimuth 方位角
     * @param horizontalFov 水平视场角
     * @param verticalFov 垂直视场角
     * @param visibleRadius 可视半径
     */
    public void updatePtzInfo(String deviceId, float pan, float tilt, float zoom,
            float azimuth, float horizontalFov, float verticalFov, float visibleRadius) {
        DevicePtzExtensionTable.PtzExtension ptzExt = database.getPtzExtension(deviceId);
        
        if (ptzExt == null) {
            // 创建新记录
            ptzExt = new DevicePtzExtensionTable.PtzExtension(deviceId);
        }
        
        ptzExt.setPan(pan);
        ptzExt.setTilt(tilt);
        ptzExt.setZoom(zoom);
        ptzExt.setAzimuth(azimuth);
        ptzExt.setHorizontalFov(horizontalFov);
        ptzExt.setVerticalFov(verticalFov);
        ptzExt.setVisibleRadius(visibleRadius);
        
        database.savePtzExtension(ptzExt);
        logger.debug("PTZ信息已更新(外部): {} -> pan={}, tilt={}, zoom={}, azimuth={}",
                deviceId, pan, tilt, zoom, azimuth);
    }
}
