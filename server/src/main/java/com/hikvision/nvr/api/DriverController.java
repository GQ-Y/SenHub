package com.hikvision.nvr.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hikvision.nvr.database.Database;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.util.*;

/**
 * 驱动配置控制器
 */
public class DriverController {
    private static final Logger logger = LoggerFactory.getLogger(DriverController.class);
    private final Database database;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DriverController(Database database) {
        this.database = database;
    }

    /**
     * 获取驱动列表
     * GET /api/drivers
     */
    public String getDrivers(Request request, Response response) {
        try {
            List<Map<String, Object>> drivers = database.getAllDrivers();
            response.status(200);
            response.type("application/json");
            return createSuccessResponse(drivers);
        } catch (Exception e) {
            logger.error("获取驱动列表失败", e);
            response.status(500);
            return createErrorResponse(500, "获取驱动列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取驱动详情
     * GET /api/drivers/:id
     */
    public String getDriver(Request request, Response response) {
        try {
            String driverId = request.params(":id");
            Map<String, Object> driver = database.getDriver(driverId);
            
            if (driver == null) {
                response.status(404);
                return createErrorResponse(404, "驱动不存在");
            }
            
            response.status(200);
            response.type("application/json");
            return createSuccessResponse(driver);
        } catch (Exception e) {
            logger.error("获取驱动详情失败", e);
            response.status(500);
            return createErrorResponse(500, "获取驱动详情失败: " + e.getMessage());
        }
    }

    /**
     * 更新驱动配置
     * PUT /api/drivers/:id
     */
    public String updateDriver(Request request, Response response) {
        try {
            String driverId = request.params(":id");
            Map<String, Object> body = objectMapper.readValue(request.body(), Map.class);
            
            String name = (String) body.getOrDefault("name", "");
            String version = (String) body.getOrDefault("version", "");
            String libPath = (String) body.get("libPath");
            String logPath = (String) body.get("logPath");
            int logLevel = body.get("logLevel") != null ? ((Number) body.get("logLevel")).intValue() : 1;
            String status = (String) body.getOrDefault("status", "INACTIVE");
            
            if (libPath == null || libPath.isEmpty()) {
                response.status(400);
                return createErrorResponse(400, "libPath不能为空");
            }
            
            // 保存驱动配置
            database.saveOrUpdateDriver(driverId, name, version, libPath, logPath, logLevel, status);
            
            // 获取更新后的驱动信息
            Map<String, Object> driver = database.getDriver(driverId);
            
            response.status(200);
            response.type("application/json");
            return createSuccessResponse(driver);
        } catch (Exception e) {
            logger.error("更新驱动配置失败", e);
            response.status(500);
            return createErrorResponse(500, "更新驱动配置失败: " + e.getMessage());
        }
    }

    /**
     * 添加新驱动
     * POST /api/drivers
     */
    public String addDriver(Request request, Response response) {
        try {
            Map<String, Object> body = objectMapper.readValue(request.body(), Map.class);
            
            String name = (String) body.get("name");
            String version = (String) body.getOrDefault("version", "1.0.0");
            String libPath = (String) body.get("libPath");
            String logPath = (String) body.getOrDefault("logPath", "/var/log/new_sdk.log");
            int logLevel = body.get("logLevel") != null ? ((Number) body.get("logLevel")).intValue() : 1;
            
            if (name == null || name.isEmpty()) {
                response.status(400);
                return createErrorResponse(400, "name不能为空");
            }
            if (libPath == null || libPath.isEmpty()) {
                response.status(400);
                return createErrorResponse(400, "libPath不能为空");
            }
            
            // 生成驱动ID
            String driverId = "drv_" + name.toLowerCase().replaceAll("[^a-z0-9]", "_");
            
            // 保存驱动配置
            database.saveOrUpdateDriver(driverId, name, version, libPath, logPath, logLevel, "INACTIVE");
            
            // 获取新创建的驱动信息
            Map<String, Object> driver = database.getDriver(driverId);
            
            response.status(201);
            response.type("application/json");
            return createSuccessResponse(driver);
        } catch (Exception e) {
            logger.error("添加驱动失败", e);
            response.status(500);
            return createErrorResponse(500, "添加驱动失败: " + e.getMessage());
        }
    }

    /**
     * 删除驱动
     * DELETE /api/drivers/:id
     */
    public String deleteDriver(Request request, Response response) {
        try {
            String driverId = request.params(":id");
            
            if (database.getDriver(driverId) == null) {
                response.status(404);
                return createErrorResponse(404, "驱动不存在");
            }
            
            database.deleteDriver(driverId);
            
            response.status(200);
            response.type("application/json");
            return createSuccessResponse(Map.of("message", "驱动已删除"));
        } catch (Exception e) {
            logger.error("删除驱动失败", e);
            response.status(500);
            return createErrorResponse(500, "删除驱动失败: " + e.getMessage());
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
