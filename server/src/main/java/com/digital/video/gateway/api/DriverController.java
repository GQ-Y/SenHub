package com.digital.video.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.digital.video.gateway.database.Database;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.Arrays;

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
    public void getDrivers(Context ctx) {
        try {
            List<Map<String, Object>> drivers = database.getAllDrivers();
            ctx.status(200);
            ctx.contentType("application/json");
            ctx.result(createSuccessResponse(drivers));
        } catch (Exception e) {
            logger.error("获取驱动列表失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "获取驱动列表失败: " + e.getMessage()));
        }
    }

    /**
     * 获取驱动详情
     * GET /api/drivers/:id
     */
    public void getDriver(Context ctx) {
        try {
            String driverId = ctx.pathParam("id");
            Map<String, Object> driver = database.getDriver(driverId);
            if (driver == null) {
                ctx.status(404);
                ctx.result(createErrorResponse(404, "驱动不存在"));
                return;
            }
            ctx.status(200);
            ctx.contentType("application/json");
            ctx.result(createSuccessResponse(driver));
        } catch (Exception e) {
            logger.error("获取驱动详情失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "获取驱动详情失败: " + e.getMessage()));
        }
    }

    /**
     * 更新驱动配置
     * PUT /api/drivers/:id
     */
    @SuppressWarnings("unchecked")
    public void updateDriver(Context ctx) {
        try {
            String driverId = ctx.pathParam("id");
            Map<String, Object> body = objectMapper.readValue(ctx.body(), Map.class);
            String name = (String) body.getOrDefault("name", "");
            String version = (String) body.getOrDefault("version", "");
            String libPath = (String) body.get("libPath");
            String logPath = (String) body.get("logPath");
            int logLevel = body.get("logLevel") != null ? ((Number) body.get("logLevel")).intValue() : 1;
            String status = (String) body.getOrDefault("status", "INACTIVE");
            if (libPath == null || libPath.isEmpty()) {
                ctx.status(400);
                ctx.result(createErrorResponse(400, "libPath不能为空"));
                return;
            }
            database.saveOrUpdateDriver(driverId, name, version, libPath, logPath, logLevel, status);
            Map<String, Object> driver = database.getDriver(driverId);
            ctx.status(200);
            ctx.contentType("application/json");
            ctx.result(createSuccessResponse(driver));
        } catch (Exception e) {
            logger.error("更新驱动配置失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "更新驱动配置失败: " + e.getMessage()));
        }
    }

    /**
     * 添加新驱动
     * POST /api/drivers
     */
    @SuppressWarnings("unchecked")
    public void addDriver(Context ctx) {
        try {
            Map<String, Object> body = objectMapper.readValue(ctx.body(), Map.class);
            String name = (String) body.get("name");
            String version = (String) body.getOrDefault("version", "1.0.0");
            String libPath = (String) body.get("libPath");
            String logPath = (String) body.getOrDefault("logPath", "/var/log/new_sdk.log");
            int logLevel = body.get("logLevel") != null ? ((Number) body.get("logLevel")).intValue() : 1;
            if (name == null || name.isEmpty()) {
                ctx.status(400);
                ctx.result(createErrorResponse(400, "name不能为空"));
                return;
            }
            if (libPath == null || libPath.isEmpty()) {
                ctx.status(400);
                ctx.result(createErrorResponse(400, "libPath不能为空"));
                return;
            }
            String driverId = "drv_" + name.toLowerCase().replaceAll("[^a-z0-9]", "_");
            database.saveOrUpdateDriver(driverId, name, version, libPath, logPath, logLevel, "INACTIVE");
            Map<String, Object> driver = database.getDriver(driverId);
            ctx.status(201);
            ctx.contentType("application/json");
            ctx.result(createSuccessResponse(driver));
        } catch (Exception e) {
            logger.error("添加驱动失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "添加驱动失败: " + e.getMessage()));
        }
    }

    /**
     * 删除驱动
     * DELETE /api/drivers/:id
     */
    public void deleteDriver(Context ctx) {
        try {
            String driverId = ctx.pathParam("id");
            if (database.getDriver(driverId) == null) {
                ctx.status(404);
                ctx.result(createErrorResponse(404, "驱动不存在"));
                return;
            }
            database.deleteDriver(driverId);
            ctx.status(200);
            ctx.contentType("application/json");
            ctx.result(createSuccessResponse(Map.of("message", "驱动已删除")));
        } catch (Exception e) {
            logger.error("删除驱动失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "删除驱动失败: " + e.getMessage()));
        }
    }

    /**
     * 检查所有SDK健康状态
     * GET /api/drivers/check-all
     */
    @SuppressWarnings("unchecked")
    public void checkAllDrivers(Context ctx) {
        try {
            List<Map<String, Object>> drivers = database.getAllDrivers();
            List<Map<String, Object>> results = new ArrayList<>();
            for (Map<String, Object> driver : drivers) {
                String driverId = (String) driver.get("id");
                Map<String, Object> checkResult = checkDriverHealth(driverId);
                if (checkResult != null) {
                    Map<String, Object> result = new HashMap<>();
                    result.put("driverId", driverId);
                    result.put("driverName", driver.get("name"));
                    result.put("health", checkResult);
                    results.add(result);
                }
            }
            ctx.status(200);
            ctx.contentType("application/json");
            ctx.result(createSuccessResponse(results));
        } catch (Exception e) {
            logger.error("检查所有SDK健康状态失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "检查所有SDK健康状态失败: " + e.getMessage()));
        }
    }

    /**
     * 检查单个SDK文件是否存在及权限（内部方法）
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> checkDriverHealth(String driverId) {
        try {
            Map<String, Object> driver = database.getDriver(driverId);
            if (driver == null) {
                return null;
            }
            
            String libPath = (String) driver.get("libPath");
            String logPath = (String) driver.get("logPath");
            
            Map<String, Object> checkResult = new HashMap<>();
            
            // 检查库文件路径
            if (libPath != null && !libPath.isEmpty()) {
                File libFile = new File(libPath);
                Map<String, Object> libCheck = new HashMap<>();
                libCheck.put("exists", libFile.exists());
                libCheck.put("isDirectory", libFile.isDirectory());
                libCheck.put("isFile", libFile.isFile());
                libCheck.put("readable", libFile.canRead());
                libCheck.put("writable", libFile.canWrite());
                libCheck.put("executable", libFile.canExecute());
                checkResult.put("libPath", libCheck);
            } else {
                Map<String, Object> libCheck = new HashMap<>();
                libCheck.put("exists", false);
                libCheck.put("error", "库文件路径未配置");
                checkResult.put("libPath", libCheck);
            }
            
            // 检查日志路径
            if (logPath != null && !logPath.isEmpty()) {
                File logFile = new File(logPath);
                Map<String, Object> logCheck = new HashMap<>();
                logCheck.put("exists", logFile.exists());
                logCheck.put("isDirectory", logFile.isDirectory());
                logCheck.put("isFile", logFile.isFile());
                logCheck.put("readable", logFile.canRead());
                logCheck.put("writable", logFile.canWrite());
                checkResult.put("logPath", logCheck);
            } else {
                Map<String, Object> logCheck = new HashMap<>();
                logCheck.put("exists", false);
                logCheck.put("error", "日志路径未配置");
                checkResult.put("logPath", logCheck);
            }
            
            return checkResult;
        } catch (Exception e) {
            logger.error("检查SDK文件失败: {}", driverId, e);
            return null;
        }
    }

    /**
     * 检查SDK文件是否存在及权限
     * GET /api/drivers/:id/check
     */
    public void checkDriver(Context ctx) {
        try {
            String driverId = ctx.pathParam("id");
            Map<String, Object> checkResult = checkDriverHealth(driverId);
            if (checkResult == null) {
                ctx.status(404);
                ctx.result(createErrorResponse(404, "驱动不存在"));
                return;
            }
            ctx.status(200);
            ctx.contentType("application/json");
            ctx.result(createSuccessResponse(checkResult));
        } catch (Exception e) {
            logger.error("检查SDK文件失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "检查SDK文件失败: " + e.getMessage()));
        }
    }

    /**
     * 获取统一的驱动日志
     * GET /api/drivers/logs
     */
    public void getDriverLogs(Context ctx) {
        try {
            String linesParam = ctx.queryParam("lines");
            int lines = linesParam != null ? Integer.parseInt(linesParam) : 100;
            if (lines > 1000) lines = 1000;
            File logFile = new File("./logs/sdk.log");
            if (!logFile.exists()) {
                File logDir = new File("./logs");
                if (logDir.exists() && logDir.isDirectory()) {
                    File[] logFiles = logDir.listFiles((dir, name) -> name.endsWith(".log") && name.startsWith("sdk"));
                    if (logFiles != null && logFiles.length > 0) {
                        Arrays.sort(logFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
                        logFile = logFiles[0];
                    }
                }
            }
            if (!logFile.exists()) {
                ctx.status(404);
                ctx.result(createErrorResponse(404, "驱动日志文件不存在"));
                return;
            }
            List<String> logLines = readLastLines(logFile, lines);
            Map<String, Object> result = new HashMap<>();
            result.put("file", logFile.getAbsolutePath());
            result.put("lines", logLines.size());
            result.put("content", logLines);
            ctx.status(200);
            ctx.contentType("application/json");
            ctx.result(createSuccessResponse(result));
        } catch (Exception e) {
            logger.error("获取驱动日志失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "获取驱动日志失败: " + e.getMessage()));
        }
    }

    /**
     * 读取文件的最后N行
     */
    private List<String> readLastLines(File file, int n) throws java.io.IOException {
        List<String> lines = new ArrayList<>();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file))) {
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
