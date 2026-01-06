package com.hikvision.nvr.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hikvision.nvr.database.DeviceInfo;
import com.hikvision.nvr.device.DeviceManager;
import com.hikvision.nvr.hikvision.HCNetSDK;
import com.hikvision.nvr.hikvision.HikvisionSDK;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 设备管理控制器
 */
public class DeviceController {
    private static final Logger logger = LoggerFactory.getLogger(DeviceController.class);
    private final DeviceManager deviceManager;
    private final HikvisionSDK sdk;
    private final com.hikvision.nvr.database.Database database;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DeviceController(DeviceManager deviceManager, HikvisionSDK sdk, com.hikvision.nvr.database.Database database) {
        this.deviceManager = deviceManager;
        this.sdk = sdk;
        this.database = database;
    }

    /**
     * 获取设备列表
     * GET /api/devices
     */
    public String getDevices(Request request, Response response) {
        try {
            List<DeviceInfo> devices = deviceManager.getAllDevices();
            
            // 支持搜索和过滤
            String search = request.queryParams("search");
            String statusFilter = request.queryParams("status");
            
            List<Map<String, Object>> deviceList = new ArrayList<>();
            for (DeviceInfo device : devices) {
                // 搜索过滤
                if (search != null && !search.isEmpty()) {
                    String searchLower = search.toLowerCase();
                    if (!device.getName().toLowerCase().contains(searchLower) && 
                        !device.getIp().contains(search)) {
                        continue;
                    }
                }
                
                // 状态过滤
                if (statusFilter != null && !statusFilter.isEmpty() && !statusFilter.equals("ALL")) {
                    if (!device.getStatus().equalsIgnoreCase(statusFilter)) {
                        continue;
                    }
                }
                
                deviceList.add(convertDeviceToMap(device));
            }
            
            response.status(200);
            response.type("application/json");
            return createSuccessResponse(deviceList);
        } catch (Exception e) {
            logger.error("获取设备列表失败", e);
            response.status(500);
            return createErrorResponse(500, "获取设备列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取设备详情
     * GET /api/devices/:id
     */
    public String getDevice(Request request, Response response) {
        try {
            String deviceId = request.params(":id");
            DeviceInfo device = deviceManager.getDevice(deviceId);
            
            if (device == null) {
                response.status(404);
                return createErrorResponse(404, "设备不存在");
            }
            
            response.status(200);
            response.type("application/json");
            return createSuccessResponse(convertDeviceToMap(device));
        } catch (Exception e) {
            logger.error("获取设备详情失败", e);
            response.status(500);
            return createErrorResponse(500, "获取设备详情失败: " + e.getMessage());
        }
    }

    /**
     * 添加设备
     * POST /api/devices
     */
    public String addDevice(Request request, Response response) {
        try {
            Map<String, Object> body = objectMapper.readValue(request.body(), Map.class);
            
            DeviceInfo device = new DeviceInfo();
            device.setDeviceId(generateDeviceId((String) body.get("ip"), (Integer) body.get("port")));
            device.setIp((String) body.get("ip"));
            device.setPort(((Number) body.get("port")).intValue());
            device.setName((String) body.get("name"));
            device.setUsername((String) body.getOrDefault("username", "admin"));
            device.setPassword((String) body.getOrDefault("password", ""));
            device.setStatus("offline");
            device.setChannel(((Number) body.getOrDefault("channel", 1)).intValue());
            
            // 生成RTSP URL
            String rtspUrl = generateRtspUrl(device.getIp(), device.getPort());
            device.setRtspUrl(rtspUrl);
            
            // 检查设备是否已存在
            DeviceInfo existingDevice = deviceManager.getDevice(device.getDeviceId());
            if (existingDevice != null) {
                // 设备已存在，更新
                device.setId(existingDevice.getId());
            }
            
            // 保存到数据库
            database.saveOrUpdateDevice(device);
            
            response.status(201);
            response.type("application/json");
            return createSuccessResponse(convertDeviceToMap(device));
        } catch (Exception e) {
            logger.error("添加设备失败", e);
            response.status(500);
            return createErrorResponse(500, "添加设备失败: " + e.getMessage());
        }
    }

    /**
     * 更新设备
     * PUT /api/devices/:id
     */
    public String updateDevice(Request request, Response response) {
        try {
            String deviceId = request.params(":id");
            DeviceInfo device = deviceManager.getDevice(deviceId);
            
            if (device == null) {
                response.status(404);
                return createErrorResponse(404, "设备不存在");
            }
            
            Map<String, Object> body = objectMapper.readValue(request.body(), Map.class);
            
            // 更新设备信息
            if (body.containsKey("name")) {
                device.setName((String) body.get("name"));
            }
            if (body.containsKey("ip")) {
                device.setIp((String) body.get("ip"));
            }
            if (body.containsKey("port")) {
                device.setPort(((Number) body.get("port")).intValue());
            }
            if (body.containsKey("username")) {
                device.setUsername((String) body.get("username"));
            }
            if (body.containsKey("password")) {
                device.setPassword((String) body.get("password"));
            }
            if (body.containsKey("channel")) {
                device.setChannel(((Number) body.get("channel")).intValue());
            }
            
            // 更新RTSP URL
            String rtspUrl = generateRtspUrl(device.getIp(), device.getPort());
            device.setRtspUrl(rtspUrl);
            
            // 保存到数据库
            database.saveOrUpdateDevice(device);
            
            response.status(200);
            response.type("application/json");
            return createSuccessResponse(convertDeviceToMap(device));
        } catch (Exception e) {
            logger.error("更新设备失败", e);
            response.status(500);
            return createErrorResponse(500, "更新设备失败: " + e.getMessage());
        }
    }

    /**
     * 删除设备
     * DELETE /api/devices/:id
     */
    public String deleteDevice(Request request, Response response) {
        try {
            String deviceId = request.params(":id");
            DeviceInfo device = deviceManager.getDevice(deviceId);
            
            if (device == null) {
                response.status(404);
                return createErrorResponse(404, "设备不存在");
            }
            
            // 登出设备
            if (deviceManager.isDeviceLoggedIn(deviceId)) {
                deviceManager.logoutDevice(deviceId);
            }
            
            // 从数据库删除
            database.deleteDevice(deviceId);
            
            response.status(200);
            response.type("application/json");
            return createSuccessResponse(Map.of("message", "设备已删除"));
        } catch (Exception e) {
            logger.error("删除设备失败", e);
            response.status(500);
            return createErrorResponse(500, "删除设备失败: " + e.getMessage());
        }
    }

    /**
     * 重启设备
     * POST /api/devices/:id/reboot
     */
    public String rebootDevice(Request request, Response response) {
        try {
            String deviceId = request.params(":id");
            DeviceInfo device = deviceManager.getDevice(deviceId);
            
            if (device == null) {
                response.status(404);
                return createErrorResponse(404, "设备不存在");
            }
            
            // 确保设备已登录
            if (!deviceManager.isDeviceLoggedIn(deviceId)) {
                if (!deviceManager.loginDevice(device)) {
                    response.status(500);
                    return createErrorResponse(500, "设备登录失败，无法重启");
                }
            }
            
            int userId = deviceManager.getDeviceUserId(deviceId);
            HCNetSDK hcNetSDK = sdk.getSDK();
            
            if (hcNetSDK == null) {
                response.status(500);
                return createErrorResponse(500, "SDK未初始化");
            }
            
            // 使用NET_DVR_RemoteControl进行远程重启
            boolean result = hcNetSDK.NET_DVR_RemoteControl(userId, HCNetSDK.MINOR_REMOTE_REBOOT, null, 0);
            
            if (!result) {
                int errorCode = sdk.getLastError();
                response.status(500);
                return createErrorResponse(500, "重启设备失败，错误码: " + errorCode);
            }
            
            Map<String, Object> data = new HashMap<>();
            data.put("message", "设备重启命令已发送");
            data.put("device_id", deviceId);
            
            response.status(200);
            response.type("application/json");
            return createSuccessResponse(data);
        } catch (Exception e) {
            logger.error("重启设备失败", e);
            response.status(500);
            return createErrorResponse(500, "重启设备失败: " + e.getMessage());
        }
    }

    /**
     * 转换DeviceInfo为Map
     */
    private Map<String, Object> convertDeviceToMap(DeviceInfo device) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", device.getDeviceId());
        map.put("name", device.getName());
        map.put("ip", device.getIp());
        map.put("port", device.getPort());
        map.put("brand", "Hikvision"); // 可以从设备信息中获取
        map.put("model", ""); // 可以从设备信息中获取
        map.put("status", device.getStatus().toUpperCase());
        map.put("lastSeen", formatLastSeen(device.getLastSeen()));
        map.put("firmware", ""); // 可以从设备信息中获取
        map.put("rtspUrl", device.getRtspUrl());
        map.put("username", device.getUsername());
        map.put("channel", device.getChannel());
        return map;
    }

    /**
     * 格式化最后发现时间
     */
    private String formatLastSeen(java.sql.Timestamp timestamp) {
        if (timestamp == null) {
            return "Never";
        }
        
        long now = System.currentTimeMillis();
        long lastSeen = timestamp.getTime();
        long diff = now - lastSeen;
        
        if (diff < 60000) { // 1分钟内
            return "Just now";
        } else if (diff < 3600000) { // 1小时内
            long mins = diff / 60000;
            return mins + " mins ago";
        } else if (diff < 86400000) { // 1天内
            long hours = diff / 3600000;
            return hours + " hours ago";
        } else {
            long days = diff / 86400000;
            return days + " days ago";
        }
    }

    /**
     * 生成设备ID
     */
    private String generateDeviceId(String ip, Integer port) {
        return ip + ":" + port;
    }

    /**
     * 生成RTSP URL
     */
    private String generateRtspUrl(String ip, int port) {
        // 默认RTSP端口是554，如果port是8000（SDK端口），则使用554
        int rtspPort = (port == 8000) ? 554 : port;
        return "rtsp://" + ip + ":" + rtspPort + "/Streaming/Channels/101";
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
