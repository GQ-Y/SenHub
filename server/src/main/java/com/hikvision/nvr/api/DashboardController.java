package com.hikvision.nvr.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hikvision.nvr.database.DeviceInfo;
import com.hikvision.nvr.device.DeviceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.util.*;

/**
 * 仪表板控制器
 */
public class DashboardController {
    private static final Logger logger = LoggerFactory.getLogger(DashboardController.class);
    private final DeviceManager deviceManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DashboardController(DeviceManager deviceManager) {
        this.deviceManager = deviceManager;
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
            int offlineDevices = 0;
            int warningDevices = 0;
            
            for (DeviceInfo device : devices) {
                String status = device.getStatus();
                if ("online".equalsIgnoreCase(status)) {
                    onlineDevices++;
                } else if ("offline".equalsIgnoreCase(status)) {
                    offlineDevices++;
                } else if ("warning".equalsIgnoreCase(status)) {
                    warningDevices++;
                }
            }
            
            double onlinePercentage = totalDevices > 0 ? (onlineDevices * 100.0 / totalDevices) : 0;
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("activeDevices", totalDevices);
            stats.put("onlineStatus", String.format("%.1f%%", onlinePercentage));
            stats.put("alerts24h", warningDevices); // 简化：使用warning设备数
            stats.put("storageUsed", "4.2 TB"); // 占位符，实际需要计算
            
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
     * 获取图表数据
     * GET /api/dashboard/chart
     */
    public String getChart(Request request, Response response) {
        try {
            // 生成模拟的24小时连接性数据
            List<Map<String, Object>> chartData = new ArrayList<>();
            
            String[] timePoints = {"00:00", "04:00", "08:00", "12:00", "16:00", "20:00", "24:00"};
            List<DeviceInfo> devices = deviceManager.getAllDevices();
            int totalDevices = devices.size();
            
            for (String time : timePoints) {
                Map<String, Object> dataPoint = new HashMap<>();
                dataPoint.put("name", time);
                // 模拟在线设备数（实际应该从历史数据中获取）
                int online = (int) (totalDevices * (0.9 + Math.random() * 0.1));
                dataPoint.put("online", online);
                dataPoint.put("offline", totalDevices - online);
                chartData.add(dataPoint);
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
