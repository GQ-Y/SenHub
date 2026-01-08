package com.hikvision.nvr.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hikvision.nvr.config.Config;
import com.hikvision.nvr.database.Database;
import com.hikvision.nvr.database.DeviceInfo;
import com.hikvision.nvr.device.DeviceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.io.File;
import java.util.*;

/**
 * 仪表板控制器
 */
public class DashboardController {
    private static final Logger logger = LoggerFactory.getLogger(DashboardController.class);
    private final DeviceManager deviceManager;
    private final Database database;
    private final Config config;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DashboardController(DeviceManager deviceManager, Database database, Config config) {
        this.deviceManager = deviceManager;
        this.database = database;
        this.config = config;
    }

    /**
     * 获取统计数据
     * GET /api/dashboard/stats
     */
    public String getStats(Request request, Response response) {
        try {
            List<DeviceInfo> devices = deviceManager.getAllDevices();
            
            int totalDevices = devices.size();
            int onlineDevices = 0;
            
            for (DeviceInfo device : devices) {
                String status = device.getStatus();
                if ("online".equalsIgnoreCase(status)) {
                    onlineDevices++;
                }
            }
            
            double onlinePercentage = totalDevices > 0 ? (onlineDevices * 100.0 / totalDevices) : 0;
            
            // 获取24小时告警数量
            int alerts24h = database.getAlarmCount24h();
            
            // 计算存储用量
            String storageUsed = calculateStorageUsed();
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("activeDevices", totalDevices);
            stats.put("onlineStatus", String.format("%.1f%%", onlinePercentage));
            stats.put("alerts24h", alerts24h);
            stats.put("storageUsed", storageUsed);
            
            response.status(200);
            response.type("application/json");
            return createSuccessResponse(stats);
        } catch (Exception e) {
            logger.error("获取统计数据失败", e);
            response.status(500);
            return createErrorResponse(500, "获取统计数据失败: " + e.getMessage());
        }
    }

    /**
     * 计算存储用量
     */
    private String calculateStorageUsed() {
        try {
            long totalBytes = 0;
            
            // 计算录制文件大小
            if (config.getRecorder() != null && config.getRecorder().getRecordPath() != null) {
                File recordDir = new File(config.getRecorder().getRecordPath());
                if (recordDir.exists() && recordDir.isDirectory()) {
                    totalBytes += getDirectorySize(recordDir);
                }
            }
            
            // 计算下载文件大小
            File downloadsDir = new File("./storage/downloads");
            if (downloadsDir.exists() && downloadsDir.isDirectory()) {
                totalBytes += getDirectorySize(downloadsDir);
            }
            
            // 转换为可读格式
            return formatBytes(totalBytes);
        } catch (Exception e) {
            logger.error("计算存储用量失败", e);
            return "0 B";
        }
    }

    /**
     * 计算目录大小
     */
    private long getDirectorySize(File directory) {
        long size = 0;
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    size += file.length();
                } else if (file.isDirectory()) {
                    size += getDirectorySize(file);
                }
            }
        }
        return size;
    }

    /**
     * 格式化字节数为可读格式
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else if (bytes < 1024L * 1024 * 1024 * 1024) {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        } else {
            return String.format("%.2f TB", bytes / (1024.0 * 1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * 获取图表数据
     * GET /api/dashboard/chart
     */
    public String getChart(Request request, Response response) {
        try {
            // 从数据库获取24小时连接趋势数据
            List<Map<String, Object>> chartData = database.getDeviceConnectivityTrend24h();
            
            // 如果历史数据不足，使用当前设备状态作为补充
            if (chartData.isEmpty() || chartData.size() < 7) {
                List<DeviceInfo> devices = deviceManager.getAllDevices();
                int onlineDevices = 0;
                for (DeviceInfo device : devices) {
                    if ("online".equalsIgnoreCase(device.getStatus())) {
                        onlineDevices++;
                    }
                }
                
                // 生成默认数据
                String[] timePoints = {"00:00", "04:00", "08:00", "12:00", "16:00", "20:00", "24:00"};
                chartData = new ArrayList<>();
                for (String time : timePoints) {
                    Map<String, Object> dataPoint = new HashMap<>();
                    dataPoint.put("name", time);
                    dataPoint.put("online", onlineDevices);
                    chartData.add(dataPoint);
                }
            }
            
            response.status(200);
            response.type("application/json");
            return createSuccessResponse(chartData);
        } catch (Exception e) {
            logger.error("获取图表数据失败", e);
            response.status(500);
            return createErrorResponse(500, "获取图表数据失败: " + e.getMessage());
        }
    }

    private String createSuccessResponse(Object data) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "success");
            response.put("data", data);
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            logger.error("创建响应失败", e);
            return "{\"code\":500,\"message\":\"Internal error\",\"data\":null}";
        }
    }

    private String createErrorResponse(int code, String message) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("code", code);
            response.put("message", message);
            response.put("data", null);
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            logger.error("创建错误响应失败", e);
            return "{\"code\":" + code + ",\"message\":\"" + message + "\",\"data\":null}";
        }
    }
}
