package com.hikvision.nvr.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hikvision.nvr.config.Config;
import com.hikvision.nvr.service.ConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.util.HashMap;
import java.util.Map;

/**
 * 系统配置控制器
 */
public class SystemController {
    private static final Logger logger = LoggerFactory.getLogger(SystemController.class);
    private final ConfigService configService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SystemController(ConfigService configService) {
        this.configService = configService;
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
