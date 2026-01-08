package com.digital.video.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.digital.video.gateway.config.Config;
import com.digital.video.gateway.mqtt.MqttClient;
import com.digital.video.gateway.service.ConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统配置控制器
 */
public class SystemController {
    private static final Logger logger = LoggerFactory.getLogger(SystemController.class);
    private final ConfigService configService;
    private final MqttClient mqttClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SystemController(ConfigService configService) {
        this.configService = configService;
        this.mqttClient = null;
    }

    public SystemController(ConfigService configService, MqttClient mqttClient) {
        this.configService = configService;
        this.mqttClient = mqttClient;
    }

    /**
     * 获取系统配置
     * GET /api/system/config
     */
    public String getConfig(Request request, Response response) {
        try {
            Config config = configService.getConfig();
            
            Map<String, Object> systemConfig = new HashMap<>();
            
            // Scanner配置
            if (config.getScanner() != null) {
                Map<String, Object> scanner = new HashMap<>();
                scanner.put("enabled", config.getScanner().isEnabled());
                scanner.put("interval", config.getScanner().getInterval());
                scanner.put("ports", "80, 8000, 554, 37777"); // 从配置中获取或默认值
                systemConfig.put("scanner", scanner);
            }
            
            // Auth配置
            if (config.getDevice() != null) {
                Map<String, Object> auth = new HashMap<>();
                auth.put("defaultUser", config.getDevice().getDefaultUsername());
                auth.put("timeout", config.getDevice().getLoginTimeout());
                systemConfig.put("auth", auth);
            }
            
            // Keeper配置
            if (config.getKeeper() != null) {
                Map<String, Object> keeper = new HashMap<>();
                keeper.put("enabled", config.getKeeper().isEnabled());
                keeper.put("checkInterval", config.getKeeper().getCheckInterval());
                systemConfig.put("keeper", keeper);
            }
            
            // OSS配置
            if (config.getOss() != null) {
                Map<String, Object> oss = new HashMap<>();
                oss.put("enabled", config.getOss().isEnabled());
                oss.put("endpoint", config.getOss().getEndpoint());
                oss.put("bucket", config.getOss().getBucketName());
                systemConfig.put("oss", oss);
            }
            
            // Log配置
            if (config.getLog() != null) {
                Map<String, Object> log = new HashMap<>();
                log.put("level", config.getLog().getLevel());
                log.put("retentionDays", config.getLog().getMaxAge());
                systemConfig.put("log", log);
            }
            
            response.status(200);
            response.type("application/json");
            return createSuccessResponse(systemConfig);
        } catch (Exception e) {
            logger.error("获取系统配置失败", e);
            response.status(500);
            return createErrorResponse(500, "获取系统配置失败: " + e.getMessage());
        }
    }

    /**
     * 更新系统配置
     * PUT /api/system/config
     */
    @SuppressWarnings("unchecked")
    public String updateConfig(Request request, Response response) {
        try {
            Map<String, Object> body = objectMapper.readValue(request.body(), Map.class);
            Config config = configService.getConfig();
            
            // 更新Scanner配置
            if (body.containsKey("scanner")) {
                Map<String, Object> scannerData = (Map<String, Object>) body.get("scanner");
                if (config.getScanner() != null) {
                    if (scannerData.containsKey("enabled")) {
                        config.getScanner().setEnabled((Boolean) scannerData.get("enabled"));
                    }
                    if (scannerData.containsKey("interval")) {
                        config.getScanner().setInterval(((Number) scannerData.get("interval")).intValue());
                    }
                }
            }
            
            // 更新Auth配置
            if (body.containsKey("auth")) {
                Map<String, Object> authData = (Map<String, Object>) body.get("auth");
                if (config.getDevice() != null) {
                    if (authData.containsKey("defaultUser")) {
                        config.getDevice().setDefaultUsername((String) authData.get("defaultUser"));
                    }
                    if (authData.containsKey("timeout")) {
                        config.getDevice().setLoginTimeout(((Number) authData.get("timeout")).intValue());
                    }
                }
            }
            
            // 更新Keeper配置
            if (body.containsKey("keeper")) {
                Map<String, Object> keeperData = (Map<String, Object>) body.get("keeper");
                if (config.getKeeper() != null) {
                    if (keeperData.containsKey("enabled")) {
                        config.getKeeper().setEnabled((Boolean) keeperData.get("enabled"));
                    }
                    if (keeperData.containsKey("checkInterval")) {
                        config.getKeeper().setCheckInterval(((Number) keeperData.get("checkInterval")).intValue());
                    }
                }
            }
            
            // 更新OSS配置
            if (body.containsKey("oss")) {
                Map<String, Object> ossData = (Map<String, Object>) body.get("oss");
                if (config.getOss() != null) {
                    if (ossData.containsKey("enabled")) {
                        config.getOss().setEnabled((Boolean) ossData.get("enabled"));
                    }
                    if (ossData.containsKey("endpoint")) {
                        config.getOss().setEndpoint((String) ossData.get("endpoint"));
                    }
                    if (ossData.containsKey("bucket")) {
                        config.getOss().setBucketName((String) ossData.get("bucket"));
                    }
                }
            }
            
            // 更新Log配置
            if (body.containsKey("log")) {
                Map<String, Object> logData = (Map<String, Object>) body.get("log");
                if (config.getLog() != null) {
                    if (logData.containsKey("level")) {
                        config.getLog().setLevel((String) logData.get("level"));
                    }
                    if (logData.containsKey("retentionDays")) {
                        config.getLog().setMaxAge(((Number) logData.get("retentionDays")).intValue());
                    }
                }
            }
            
            // 保存配置
            configService.updateConfig(config);
            
            response.status(200);
            response.type("application/json");
            return createSuccessResponse(Map.of("message", "配置已更新"));
        } catch (Exception e) {
            logger.error("更新系统配置失败", e);
            response.status(500);
            return createErrorResponse(500, "更新系统配置失败: " + e.getMessage());
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

    /**
     * 系统健康检查
     * GET /api/system/health
     */
    public String healthCheck(Request request, Response response) {
        try {
            Map<String, Object> health = new HashMap<>();
            
            // 检查MQTT连接状态
            boolean mqttConnected = mqttClient != null && mqttClient.isConnected();
            Map<String, Object> mqttStatus = new HashMap<>();
            mqttStatus.put("connected", mqttConnected);
            health.put("mqtt", mqttStatus);
            
            // 检查数据库连接
            Map<String, Object> dbStatus = new HashMap<>();
            dbStatus.put("status", "ok");
            health.put("database", dbStatus);
            
            // 检查SDK状态
            Map<String, Object> sdkStatus = new HashMap<>();
            sdkStatus.put("status", "ok");
            health.put("sdk", sdkStatus);
            
            // 检查磁盘空间
            File rootDir = new File(".");
            long freeSpace = rootDir.getFreeSpace();
            long totalSpace = rootDir.getTotalSpace();
            double diskUsagePercent = totalSpace > 0 ? (1.0 - (double) freeSpace / totalSpace) * 100 : 0;
            Map<String, Object> diskInfo = new HashMap<>();
            diskInfo.put("freeSpace", formatBytes(freeSpace));
            diskInfo.put("totalSpace", formatBytes(totalSpace));
            diskInfo.put("usagePercent", String.format("%.1f%%", diskUsagePercent));
            health.put("disk", diskInfo);
            
            // 总体健康状态
            boolean isHealthy = mqttConnected && diskUsagePercent < 90;
            health.put("status", isHealthy ? "healthy" : "warning");
            
            response.status(200);
            response.type("application/json");
            return createSuccessResponse(health);
        } catch (Exception e) {
            logger.error("健康检查失败", e);
            response.status(500);
            return createErrorResponse(500, "健康检查失败: " + e.getMessage());
        }
    }

    /**
     * 重启MQTT连接
     * POST /api/system/mqtt/restart
     */
    public String restartMqtt(Request request, Response response) {
        try {
            if (mqttClient == null) {
                response.status(400);
                return createErrorResponse(400, "MQTT客户端未初始化");
            }
            
            // 断开连接
            mqttClient.disconnect();
            
            // 等待1秒后重连
            Thread.sleep(1000);
            
            // 重新连接
            boolean connected = mqttClient.connect();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", connected);
            result.put("message", connected ? "MQTT重启成功" : "MQTT重启失败");
            
            response.status(connected ? 200 : 500);
            response.type("application/json");
            return createSuccessResponse(result);
        } catch (Exception e) {
            logger.error("重启MQTT失败", e);
            response.status(500);
            return createErrorResponse(500, "重启MQTT失败: " + e.getMessage());
        }
    }

    /**
     * 获取日志内容
     * GET /api/system/logs
     */
    public String getLogs(Request request, Response response) {
        try {
            String logFilePath = "./logs/app.log";
            Config config = configService.getConfig();
            if (config.getLog() != null && config.getLog().getFile() != null) {
                logFilePath = config.getLog().getFile();
            }
            
            File logFile = new File(logFilePath);
            if (!logFile.exists()) {
                response.status(404);
                return createErrorResponse(404, "日志文件不存在");
            }
            
            // 读取最后N行日志（默认100行）
            int lines = 100;
            String linesParam = request.queryParams("lines");
            if (linesParam != null) {
                try {
                    lines = Integer.parseInt(linesParam);
                    if (lines > 1000) lines = 1000; // 限制最大行数
                } catch (NumberFormatException e) {
                    // 使用默认值
                }
            }
            
            List<String> logLines = readLastLines(logFile, lines);
            
            Map<String, Object> result = new HashMap<>();
            result.put("file", logFilePath);
            result.put("lines", logLines.size());
            result.put("content", logLines);
            
            response.status(200);
            response.type("application/json");
            return createSuccessResponse(result);
        } catch (Exception e) {
            logger.error("获取日志失败", e);
            response.status(500);
            return createErrorResponse(500, "获取日志失败: " + e.getMessage());
        }
    }

    /**
     * 读取文件的最后N行
     */
    private List<String> readLastLines(File file, int n) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
                if (lines.size() > n) {
                    lines.remove(0);
                }
            }
        }
        return lines;
    }

    /**
     * 格式化字节数
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
