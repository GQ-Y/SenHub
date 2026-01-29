package com.digital.video.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.digital.video.gateway.database.DeviceInfo;
import com.digital.video.gateway.device.DeviceManager;
import com.digital.video.gateway.scanner.DeviceScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 设备扫描控制器
 * 支持手动扫描设备和添加扫描结果到数据库
 */
public class ScannerController {
    private static final Logger logger = LoggerFactory.getLogger(ScannerController.class);
    private final DeviceScanner scanner;
    private final DeviceManager deviceManager;
    private final com.digital.video.gateway.database.Database database;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 存储手动扫描的结果（不写入数据库）
    private final Map<String, ScanSession> scanSessions = new ConcurrentHashMap<>();
    private final AtomicInteger sessionIdCounter = new AtomicInteger(0);

    public ScannerController(DeviceScanner scanner, DeviceManager deviceManager,
            com.digital.video.gateway.database.Database database) {
        this.scanner = scanner;
        this.deviceManager = deviceManager;
        this.database = database;
    }

    /**
     * 扫描会话信息
     */
    public static class ScanSession {
        private String sessionId;
        private String status; // "scanning", "completed", "error"
        private int totalScanned;
        private int successCount;
        private int failedCount;
        private List<ScannedDevice> devices;
        private String errorMessage;
        private long startTime;
        private long endTime;

        public ScanSession(String sessionId) {
            this.sessionId = sessionId;
            this.status = "scanning";
            this.totalScanned = 0;
            this.successCount = 0;
            this.failedCount = 0;
            this.devices = new ArrayList<>();
            this.startTime = System.currentTimeMillis();
        }

        // Getters and Setters
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public int getTotalScanned() { return totalScanned; }
        public void setTotalScanned(int totalScanned) { this.totalScanned = totalScanned; }
        public int getSuccessCount() { return successCount; }
        public void setSuccessCount(int successCount) { this.successCount = successCount; }
        public int getFailedCount() { return failedCount; }
        public void setFailedCount(int failedCount) { this.failedCount = failedCount; }
        public List<ScannedDevice> getDevices() { return devices; }
        public void setDevices(List<ScannedDevice> devices) { this.devices = devices; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public long getStartTime() { return startTime; }
        public void setStartTime(long startTime) { this.startTime = startTime; }
        public long getEndTime() { return endTime; }
        public void setEndTime(long endTime) { this.endTime = endTime; }
    }

    /**
     * 扫描到的设备信息（不写入数据库）
     */
    public static class ScannedDevice {
        private String ip;
        private int port;
        private String name;
        private String brand;
        private boolean loginSuccess;
        private String errorMessage;
        private int userId;
        private String username;
        private String password;

        public ScannedDevice() {}

        public ScannedDevice(String ip, int port, String name, String brand, boolean loginSuccess) {
            this.ip = ip;
            this.port = port;
            this.name = name;
            this.brand = brand;
            this.loginSuccess = loginSuccess;
        }

        // Getters and Setters
        public String getIp() { return ip; }
        public void setIp(String ip) { this.ip = ip; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getBrand() { return brand; }
        public void setBrand(String brand) { this.brand = brand; }
        public boolean isLoginSuccess() { return loginSuccess; }
        public void setLoginSuccess(boolean loginSuccess) { this.loginSuccess = loginSuccess; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public int getUserId() { return userId; }
        public void setUserId(int userId) { this.userId = userId; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    /**
     * 启动手动扫描
     * POST /api/scanner/start
     */
    public String startScan(Request request, Response response) {
        try {
            // 创建新的扫描会话
            String sessionId = "scan_" + sessionIdCounter.incrementAndGet() + "_" + System.currentTimeMillis();
            ScanSession session = new ScanSession(sessionId);
            scanSessions.put(sessionId, session);

            // 在后台线程中执行扫描
            new Thread(() -> {
                try {
                    session.setStatus("scanning");
                    logger.info("开始手动扫描，会话ID: {}", sessionId);

                    // 执行扫描（不写入数据库）
                    List<ScannedDevice> scannedDevices = scanner.manualScan(session);

                    session.setDevices(scannedDevices);
                    session.setSuccessCount((int) scannedDevices.stream().filter(ScannedDevice::isLoginSuccess).count());
                    session.setFailedCount((int) scannedDevices.stream().filter(d -> !d.isLoginSuccess()).count());
                    session.setTotalScanned(scannedDevices.size());
                    session.setStatus("completed");
                    session.setEndTime(System.currentTimeMillis());

                    logger.info("手动扫描完成，会话ID: {}，发现设备: {}，登录成功: {}", 
                            sessionId, scannedDevices.size(), session.getSuccessCount());
                } catch (Exception e) {
                    logger.error("手动扫描失败，会话ID: {}", sessionId, e);
                    session.setStatus("error");
                    session.setErrorMessage(e.getMessage());
                    session.setEndTime(System.currentTimeMillis());
                }
            }, "ManualScan-" + sessionId).start();

            response.status(200);
            response.type("application/json");
            Map<String, Object> result = new HashMap<>();
            result.put("sessionId", sessionId);
            result.put("status", "scanning");
            return createSuccessResponse(result);
        } catch (Exception e) {
            logger.error("启动扫描失败", e);
            response.status(500);
            return createErrorResponse(500, "启动扫描失败: " + e.getMessage());
        }
    }

    /**
     * 获取扫描进度和结果
     * GET /api/scanner/status/:sessionId
     */
    public String getScanStatus(Request request, Response response) {
        try {
            String sessionId = request.params(":sessionId");
            ScanSession session = scanSessions.get(sessionId);

            if (session == null) {
                response.status(404);
                return createErrorResponse(404, "扫描会话不存在");
            }

            response.status(200);
            response.type("application/json");
            return createSuccessResponse(convertSessionToMap(session));
        } catch (Exception e) {
            logger.error("获取扫描状态失败", e);
            response.status(500);
            return createErrorResponse(500, "获取扫描状态失败: " + e.getMessage());
        }
    }

    /**
     * 添加扫描到的设备到数据库
     * POST /api/scanner/add-devices
     */
    @SuppressWarnings("unchecked")
    public String addDevices(Request request, Response response) {
        try {
            Map<String, Object> body = objectMapper.readValue(request.body(), Map.class);
            String sessionId = (String) body.get("sessionId");
            @SuppressWarnings("unchecked")
            List<String> deviceIps = (List<String>) body.get("deviceIps");

            if (sessionId == null || deviceIps == null || deviceIps.isEmpty()) {
                response.status(400);
                return createErrorResponse(400, "参数错误：需要sessionId和deviceIps");
            }

            ScanSession session = scanSessions.get(sessionId);
            if (session == null) {
                response.status(404);
                return createErrorResponse(404, "扫描会话不存在");
            }

            int addedCount = 0;
            int failedCount = 0;
            int skippedCount = 0;
            List<String> errors = new ArrayList<>();
            List<String> addedDevices = new ArrayList<>();

            for (String deviceIp : deviceIps) {
                try {
                    // 从扫描结果中查找设备
                    ScannedDevice scannedDevice = session.getDevices().stream()
                            .filter(d -> d.getIp().equals(deviceIp))
                            .findFirst()
                            .orElse(null);

                    if (scannedDevice == null || !scannedDevice.isLoginSuccess()) {
                        failedCount++;
                        errors.add("设备 " + deviceIp + " 未找到或登录失败");
                        continue;
                    }

                    // 检查设备是否已存在
                    DeviceInfo existingDevice = database.getDeviceByIpPort(scannedDevice.getIp(), scannedDevice.getPort());
                    if (existingDevice != null) {
                        skippedCount++;
                        errors.add("设备 " + deviceIp + " 已存在，已跳过");
                        logger.debug("设备已存在，跳过: {}:{}", scannedDevice.getIp(), scannedDevice.getPort());
                        continue;
                    }

                    // 创建设备信息并保存到数据库（新设备使用虚拟 ID，国标 ID 由用户在前端主动设置）
                    DeviceInfo device = new DeviceInfo();
                    device.setDeviceId("v_" + java.util.UUID.randomUUID().toString().replace("-", ""));
                    device.setIp(scannedDevice.getIp());
                    device.setPort(scannedDevice.getPort());
                    device.setName(scannedDevice.getName() != null ? scannedDevice.getName() : "未知设备");
                    device.setBrand(scannedDevice.getBrand());
                    device.setStatus(1); // 已登录成功
                    device.setUserId(scannedDevice.getUserId());
                    device.setChannel(1); // 默认通道1

                    // 从扫描结果中获取用户名和密码
                    device.setUsername(scannedDevice.getUsername() != null ? scannedDevice.getUsername() : "admin");
                    device.setPassword(scannedDevice.getPassword() != null ? scannedDevice.getPassword() : "");

                    // 生成RTSP URL
                    device.setRtspUrl(String.format("rtsp://%s:%d/Streaming/Channels/101", 
                            scannedDevice.getIp(), 554));

                    // 保存到数据库
                    database.saveOrUpdateDevice(device);
                    addedCount++;
                    addedDevices.add(deviceIp);

                    logger.info("已添加扫描设备到数据库: {}:{} (品牌: {})", 
                            scannedDevice.getIp(), scannedDevice.getPort(), scannedDevice.getBrand());
                } catch (Exception e) {
                    logger.error("添加设备失败: {}", deviceIp, e);
                    failedCount++;
                    errors.add("设备 " + deviceIp + " 添加失败: " + e.getMessage());
                }
            }

            response.status(200);
            response.type("application/json");
            Map<String, Object> result = new HashMap<>();
            result.put("addedCount", addedCount);
            result.put("skippedCount", skippedCount);
            result.put("failedCount", failedCount);
            result.put("addedDevices", addedDevices);
            result.put("errors", errors);
            return createSuccessResponse(result);
        } catch (Exception e) {
            logger.error("添加设备失败", e);
            response.status(500);
            return createErrorResponse(500, "添加设备失败: " + e.getMessage());
        }
    }

    /**
     * 转换扫描会话为Map
     */
    private Map<String, Object> convertSessionToMap(ScanSession session) {
        Map<String, Object> map = new HashMap<>();
        map.put("sessionId", session.getSessionId());
        map.put("status", session.getStatus());
        map.put("totalScanned", session.getTotalScanned());
        map.put("successCount", session.getSuccessCount());
        map.put("failedCount", session.getFailedCount());
        map.put("errorMessage", session.getErrorMessage());
        map.put("startTime", session.getStartTime());
        map.put("endTime", session.getEndTime());

        List<Map<String, Object>> devices = new ArrayList<>();
        for (ScannedDevice device : session.getDevices()) {
            Map<String, Object> deviceMap = new HashMap<>();
            deviceMap.put("ip", device.getIp());
            deviceMap.put("port", device.getPort());
            deviceMap.put("name", device.getName());
            deviceMap.put("brand", device.getBrand());
            deviceMap.put("loginSuccess", device.isLoginSuccess());
            deviceMap.put("errorMessage", device.getErrorMessage());
            deviceMap.put("userId", device.getUserId());
            deviceMap.put("username", device.getUsername());
            deviceMap.put("password", device.getPassword());
            devices.add(deviceMap);
        }
        map.put("devices", devices);

        return map;
    }

    /**
     * 创建成功响应
     */
    private String createSuccessResponse(Object data) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("data", data);
            response.put("message", "success");
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            logger.error("创建响应失败", e);
            return "{\"code\":500,\"message\":\"创建响应失败\"}";
        }
    }

    /**
     * 创建错误响应
     */
    private String createErrorResponse(int code, String message) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("code", code);
            response.put("data", null);
            response.put("message", message);
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            logger.error("创建错误响应失败", e);
            return "{\"code\":500,\"message\":\"创建错误响应失败\"}";
        }
    }
}
