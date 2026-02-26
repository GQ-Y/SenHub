package com.digital.video.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.digital.video.gateway.config.Config;
import com.digital.video.gateway.database.Database;
import com.digital.video.gateway.database.DeviceInfo;
import com.digital.video.gateway.device.DeviceManager;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public void getStats(Context ctx) {
        try {
            List<DeviceInfo> devices = deviceManager.getAllDevices();
            int totalDevices = devices.size();
            int onlineDevices = 0;
            for (DeviceInfo device : devices) {
                if (device.getStatus() == 1) onlineDevices++;
            }
            double onlinePercentage = totalDevices > 0 ? (onlineDevices * 100.0 / totalDevices) : 0;
            int alerts24h = database.getAlarmCount24h();
            Map<String, Object> storageInfo = calculateSystemStorage();

            Map<String, Object> stats = new HashMap<>();
            stats.put("activeDevices", totalDevices);
            stats.put("onlineStatus", String.format("%.1f%%", onlinePercentage));
            stats.put("alerts24h", alerts24h);
            stats.put("storageUsed", storageInfo.get("used"));
            stats.put("storageTotal", storageInfo.get("total"));
            stats.put("storagePercent", storageInfo.get("percent"));

            ctx.status(200);
            ctx.contentType("application/json");
            ctx.result(createSuccessResponse(stats));
        } catch (Exception e) {
            logger.error("获取统计数据失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "获取统计数据失败: " + e.getMessage()));
        }
    }

    private Map<String, Object> calculateSystemStorage() {
        Map<String, Object> storageInfo = new HashMap<>();
        try {
            File rootDir = new File(".");
            long totalSpace = rootDir.getTotalSpace();
            long freeSpace = rootDir.getFreeSpace();
            long usedSpace = totalSpace - freeSpace;
            double usagePercent = totalSpace > 0 ? (usedSpace * 100.0 / totalSpace) : 0;
            storageInfo.put("used", formatBytes(usedSpace));
            storageInfo.put("total", formatBytes(totalSpace));
            storageInfo.put("percent", String.format("%.1f%%", usagePercent));
        } catch (Exception e) {
            logger.error("计算系统存储用量失败", e);
            storageInfo.put("used", "0 B");
            storageInfo.put("total", "0 B");
            storageInfo.put("percent", "0.0%");
        }
        return storageInfo;
    }

    private long getDirectorySize(File directory) {
        long size = 0;
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) size += file.length();
                else if (file.isDirectory()) size += getDirectorySize(file);
            }
        }
        return size;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        if (bytes < 1024L * 1024 * 1024 * 1024) return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        return String.format("%.2f TB", bytes / (1024.0 * 1024.0 * 1024.0 * 1024.0));
    }

    public void getChart(Context ctx) {
        try {
            List<Map<String, Object>> alarmTrendData = database.getAlarmEventTrend24h();
            List<Map<String, Object>> workflowTrendData = database.getWorkflowExecutionTrend24h();
            List<Map<String, Object>> chartData = new ArrayList<>();
            String[] timePoints = { "00:00", "04:00", "08:00", "12:00", "16:00", "20:00" };

            Map<String, Integer> alarmMap = new HashMap<>();
            for (Map<String, Object> data : alarmTrendData) {
                String name = (String) data.get("name");
                Integer alarms = (Integer) data.get("alarms");
                alarmMap.put(name, alarms != null ? alarms : 0);
            }
            Map<String, Integer> workflowMap = new HashMap<>();
            for (Map<String, Object> data : workflowTrendData) {
                String name = (String) data.get("name");
                Integer workflows = (Integer) data.get("workflows");
                workflowMap.put(name, workflows != null ? workflows : 0);
            }
            for (String time : timePoints) {
                Map<String, Object> dataPoint = new HashMap<>();
                dataPoint.put("name", time);
                dataPoint.put("alarms", alarmMap.getOrDefault(time, 0));
                dataPoint.put("workflows", workflowMap.getOrDefault(time, 0));
                chartData.add(dataPoint);
            }

            ctx.status(200);
            ctx.contentType("application/json");
            ctx.result(createSuccessResponse(chartData));
        } catch (Exception e) {
            logger.error("获取图表数据失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "获取图表数据失败: " + e.getMessage()));
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
