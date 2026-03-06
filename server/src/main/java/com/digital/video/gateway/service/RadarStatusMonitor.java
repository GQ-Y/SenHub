package com.digital.video.gateway.service;

import com.digital.video.gateway.database.Database;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 雷达在线状态监控。
 *
 * 之前版本使用 ICMP Ping 判断在线状态，存在两个问题：
 *   1. ICMP 可达不等于 Livox SDK 连通（雷达不响应 ICMP 也能推点云）；
 *   2. Ping 结果与 SDK DeviceInfoCallback 相互覆盖，导致状态抖动。
 *
 * 当前版本移除 ICMP Ping，在线/离线状态完全由以下两个来源决定，二者一致无冲突：
 *   - SDK DeviceInfoCallback（设备连接/断开时触发，由 RadarService.updateDeviceStatusByIpOrSerial 写入）
 *   - 点云超时检测（RadarService.checkPointCloudTimeout，30 秒无数据则标记离线）
 *
 * 本类保留启动/停止接口以兼容调用方，但定时任务已置空（仅保留日志）。
 * 若未来需要额外的探活手段（如 SDK 心跳查询），可在 checkAll() 中扩展。
 */
public class RadarStatusMonitor {
    private static final Logger logger = LoggerFactory.getLogger(RadarStatusMonitor.class);

    private ScheduledExecutorService executorService;

    public RadarStatusMonitor(Database database) {
        // database 参数保留，兼容调用方签名；ICMP 逻辑已移除，此处不再使用
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
        logger.info("RadarStatusMonitor 已启动（状态由 SDK 回调和点云超时检测维护，ICMP Ping 已禁用）");
    }

    public void stop() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }
}
