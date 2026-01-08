package com.digital.video.gateway.keeper;

import com.digital.video.gateway.config.Config;
import com.digital.video.gateway.device.DeviceManager;
import com.digital.video.gateway.database.DeviceInfo;
import com.digital.video.gateway.recorder.Recorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 保活系统
 * 定期检查设备在线状态，自动重连离线设备
 */
public class Keeper {
    private static final Logger logger = LoggerFactory.getLogger(Keeper.class);
    private DeviceManager deviceManager;
    private Config.KeeperConfig config;
    private Recorder recorder;
    private ScheduledExecutorService scheduler;
    private final Map<String, Integer> failureCountMap = new ConcurrentHashMap<>();
    private boolean running = false;

    public Keeper(DeviceManager deviceManager, Config.KeeperConfig config) {
        this.deviceManager = deviceManager;
        this.config = config;
    }

    public Keeper(DeviceManager deviceManager, Config.KeeperConfig config, Recorder recorder) {
        this.deviceManager = deviceManager;
        this.config = config;
        this.recorder = recorder;
    }

    /**
     * 启动保活系统
     */
    public void start() {
        if (running) {
            logger.warn("保活系统已在运行");
            return;
        }

        if (!config.isEnabled()) {
            logger.info("保活系统已禁用");
            return;
        }

        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleWithFixedDelay(
            this::checkDevices,
            0,
            config.getCheckInterval(),
            TimeUnit.SECONDS
        );

        running = true;
        logger.info("保活系统已启动 - 检查间隔: {}秒", config.getCheckInterval());
    }

    /**
     * 停止保活系统
     */
    public void stop() {
        if (!running) {
            return;
        }

        if (scheduler != null) {
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
        logger.info("保活系统已停止");
    }

    /**
     * 检查所有设备状态
     */
    private void checkDevices() {
        try {
            List<DeviceInfo> devices = deviceManager.getAllDevices();
            logger.debug("开始检查设备状态，设备数量: {}", devices.size());

            for (DeviceInfo device : devices) {
                checkDevice(device);
            }
        } catch (Exception e) {
            logger.error("检查设备状态失败", e);
        }
    }

    /**
     * 检查单个设备状态
     */
    private void checkDevice(DeviceInfo device) {
        String deviceId = device.getDeviceId();
        boolean isLoggedIn = deviceManager.isDeviceLoggedIn(deviceId);

        if (isLoggedIn) {
            // 设备已登录，重置失败计数
            failureCountMap.remove(deviceId);
            if (!"online".equals(device.getStatus())) {
                deviceManager.updateDeviceStatus(deviceId, "online");
                logger.debug("设备状态更新为在线: {}", deviceId);
            }
        } else {
            // 设备未登录，尝试登录
            int failureCount = failureCountMap.getOrDefault(deviceId, 0);
            
            if (deviceManager.loginDevice(device)) {
                // 登录成功
                failureCountMap.remove(deviceId);
                logger.info("设备自动登录成功: {}", deviceId);
                
                // 如果录制功能启用，自动启动录制
                if (recorder != null && config != null) {
                    try {
                        recorder.startRecording(deviceId);
                    } catch (Exception e) {
                        logger.error("设备登录后启动录制失败: {}", deviceId, e);
                    }
                }
            } else {
                // 登录失败
                failureCount++;
                failureCountMap.put(deviceId, failureCount);
                
                if (failureCount >= config.getOfflineThreshold()) {
                    // 达到离线阈值，更新状态为离线
                    if (!"offline".equals(device.getStatus())) {
                        deviceManager.updateDeviceStatus(deviceId, "offline");
                        logger.warn("设备判定为离线: {} (连续失败{}次)", deviceId, failureCount);
                    }
                } else {
                    logger.debug("设备登录失败: {} (失败{}次/{})", deviceId, failureCount, config.getOfflineThreshold());
                }
            }
        }
    }

    /**
     * 检查是否正在运行
     */
    public boolean isRunning() {
        return running;
    }
}
