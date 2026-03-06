package com.digital.video.gateway.service;

import com.digital.video.gateway.database.Database;
import com.digital.video.gateway.database.DeviceInfo;
import com.digital.video.gateway.device.DeviceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 设备在线率时序采集器
 * 每60秒统计一次在线/总设备数，写入 device_metrics 表
 */
public class DeviceMetricsCollector {
    private static final Logger logger = LoggerFactory.getLogger(DeviceMetricsCollector.class);

    private final Database database;
    private final DeviceManager deviceManager;
    private final ScheduledExecutorService scheduler;
    private static final int INTERVAL_SECONDS = 60;

    public DeviceMetricsCollector(Database database, DeviceManager deviceManager) {
        this.database = database;
        this.deviceManager = deviceManager;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "DeviceMetricsCollector");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::collect, INTERVAL_SECONDS, INTERVAL_SECONDS, TimeUnit.SECONDS);
        logger.info("设备在线率采集器已启动，采集间隔: {}秒", INTERVAL_SECONDS);
    }

    private void collect() {
        try {
            List<DeviceInfo> devices = deviceManager.getAllDevices();
            int total = devices.size();
            int online = (int) devices.stream().filter(d -> d.getStatus() == 1).count();
            database.insertDeviceMetrics(total, online);
            logger.debug("设备在线率采集: total={}, online={}", total, online);
        } catch (Exception e) {
            logger.error("设备在线率采集失败", e);
        }
    }

    public void stop() {
        scheduler.shutdown();
        try {
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        logger.info("设备在线率采集器已停止");
    }
}
