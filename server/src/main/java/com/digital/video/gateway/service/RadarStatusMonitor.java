package com.digital.video.gateway.service;

import com.digital.video.gateway.database.Database;
import com.digital.video.gateway.database.RadarDevice;
import com.digital.video.gateway.database.RadarDeviceDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 周期性检查雷达在线状态并同步到数据库。
 * 当 SDK 未提供可靠的回调时，作为兜底方案使用。
 */
public class RadarStatusMonitor {
    private static final Logger logger = LoggerFactory.getLogger(RadarStatusMonitor.class);

    private final RadarDeviceDAO radarDeviceDAO;
    private ScheduledExecutorService executorService;

    public RadarStatusMonitor(Database database) {
        this.radarDeviceDAO = new RadarDeviceDAO(database.getConnection());
    }

    public void start() {
        if (executorService != null && !executorService.isShutdown()) {
            return;
        }
        executorService = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "RadarStatusMonitor");
            t.setDaemon(true);
            return t;
        });
        executorService.scheduleAtFixedRate(this::checkAll, 5, 30, TimeUnit.SECONDS);
        logger.info("RadarStatusMonitor 已启动");
    }

    public void stop() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    private void checkAll() {
        try {
            List<RadarDevice> devices = radarDeviceDAO.getAll();
            for (RadarDevice device : devices) {
                String ip = device.getRadarIp();
                boolean reachable = isReachable(ip);
                int newStatus = reachable ? 1 : 0;
                if (device.getStatus() != newStatus) {
                    radarDeviceDAO.updateStatus(device.getDeviceId(), newStatus);
                    logger.info("雷达状态变更: deviceId={}, ip={}, status={}", device.getDeviceId(), ip, newStatus);
                }
            }
        } catch (Exception e) {
            logger.error("RadarStatusMonitor 检查过程出现异常", e);
        }
    }

    private boolean isReachable(String ip) {
        try {
            InetAddress address = InetAddress.getByName(ip);
            return address.isReachable(2000);
        } catch (Exception e) {
            logger.debug("雷达IP不可达: {}", ip);
            return false;
        }
    }
}
