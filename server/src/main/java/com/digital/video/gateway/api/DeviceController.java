package com.digital.video.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.digital.video.gateway.database.DeviceInfo;
import com.digital.video.gateway.device.DeviceManager;
import com.digital.video.gateway.device.DeviceSDK;
import com.digital.video.gateway.hikvision.HCNetSDK;
import com.digital.video.gateway.hikvision.HikvisionSDK;
import com.digital.video.gateway.recorder.Recorder;
import com.digital.video.gateway.service.CaptureService;
import com.digital.video.gateway.service.PTZService;
import com.digital.video.gateway.service.PlaybackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 设备管理控制器
 * 使用功能服务类实现多品牌SDK支持
 */
public class DeviceController {
    private static final Logger logger = LoggerFactory.getLogger(DeviceController.class);
    private final DeviceManager deviceManager;
    private final com.digital.video.gateway.database.Database database;
    private final Recorder recorder;
    private final CaptureService captureService;
    private final PTZService ptzService;
    private final PlaybackService playbackService;
    private final HikvisionSDK sdk; // 保留用于重启等特殊功能（仅海康设备）
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DeviceController(DeviceManager deviceManager, com.digital.video.gateway.database.Database database, 
                          Recorder recorder, CaptureService captureService, PTZService ptzService, 
                          PlaybackService playbackService, HikvisionSDK sdk) {
        this.deviceManager = deviceManager;
        this.database = database;
        this.recorder = recorder;
        this.captureService = captureService;
        this.ptzService = ptzService;
        this.playbackService = playbackService;
        this.sdk = sdk; // 保留用于重启等特殊功能
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
            // 设置品牌，如果未指定则使用auto（自动检测）
            String brand = (String) body.getOrDefault("brand", DeviceInfo.BRAND_AUTO);
            device.setBrand(brand != null ? brand : DeviceInfo.BRAND_AUTO);
            
            // 生成包含认证信息的RTSP URL
            String rtspUrl = generateRtspUrlWithAuth(device.getIp(), device.getPort(), device.getUsername(), device.getPassword());
            device.setRtspUrl(rtspUrl);
            
            // 检查设备是否已存在
            DeviceInfo existingDevice = deviceManager.getDevice(device.getDeviceId());
            if (existingDevice != null) {
                // 设备已存在，更新
                device.setId(existingDevice.getId());
            }
            
            // 保存到数据库
            database.saveOrUpdateDevice(device);
            
            // 立即触发登录
            logger.info("设备保存成功，立即尝试登录: {}", device.getDeviceId());
            deviceManager.loginDevice(device);
            
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
            if (body.containsKey("brand")) {
                String brand = (String) body.get("brand");
                if (brand != null && !brand.isEmpty()) {
                    device.setBrand(brand);
                }
            }
            
            // 更新RTSP URL
            String rtspUrl = generateRtspUrlWithAuth(device.getIp(), device.getPort(), device.getUsername(), device.getPassword());
            device.setRtspUrl(rtspUrl);
            
            // 保存到数据库
            database.saveOrUpdateDevice(device);
            
            // 如果设备信息发生变化（IP、端口、用户名、密码、品牌），立即触发登录
            boolean needRelogin = body.containsKey("ip") || body.containsKey("port") || 
                                 body.containsKey("username") || body.containsKey("password") || 
                                 body.containsKey("brand");
            if (needRelogin) {
                // 如果设备已登录，先登出
                if (deviceManager.isDeviceLoggedIn(deviceId)) {
                    deviceManager.logoutDevice(deviceId);
                }
                // 立即触发登录
                logger.info("设备信息已更新，立即尝试重新登录: {}", deviceId);
                deviceManager.loginDevice(device);
            }
            
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
            
            // 使用NET_DVR_RebootDVR进行远程重启（更简单直接）
            // 如果设备不支持NET_DVR_RebootDVR，则使用NET_DVR_RemoteControl
            boolean result = false;
            try {
                result = hcNetSDK.NET_DVR_RebootDVR(userId);
            } catch (Exception e) {
                // 如果NET_DVR_RebootDVR不可用，尝试使用NET_DVR_RemoteControl
                logger.debug("NET_DVR_RebootDVR不可用，尝试使用NET_DVR_RemoteControl: {}", e.getMessage());
                result = hcNetSDK.NET_DVR_RemoteControl(userId, HCNetSDK.MINOR_REMOTE_REBOOT, null, 0);
            }
            
            if (!result) {
                int errorCode = sdk.getLastError();
                String errorMsg = "重启设备失败，错误码: " + errorCode;
                // 错误码17表示参数错误
                if (errorCode == 17) {
                    errorMsg += " (参数错误，可能是设备不支持重启功能或用户权限不足)";
                }
                logger.error(errorMsg);
                response.status(500);
                return createErrorResponse(500, errorMsg);
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
        // 从设备信息中获取brand，如果为空则使用默认值
        String brand = device.getBrand();
        if (brand == null || brand.isEmpty()) {
            brand = DeviceInfo.BRAND_AUTO;
        }
        map.put("brand", brand);
        map.put("model", ""); // 可以从设备信息中获取
        map.put("status", device.getStatus().toUpperCase());
        map.put("lastSeen", formatLastSeen(device.getLastSeen()));
        map.put("firmware", ""); // 可以从设备信息中获取
        
        // 如果rtspUrl不包含认证信息，生成包含认证信息的URL
        String rtspUrl = device.getRtspUrl();
        if (rtspUrl == null || rtspUrl.isEmpty() || !rtspUrl.contains("@")) {
            rtspUrl = generateRtspUrlWithAuth(device.getIp(), device.getPort(), device.getUsername(), device.getPassword());
        }
        map.put("rtspUrl", rtspUrl);
        map.put("username", device.getUsername());
        map.put("password", device.getPassword()); // 也返回密码，前端可能需要
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

    /**
     * 生成包含认证信息的RTSP URL
     */
    private String generateRtspUrlWithAuth(String ip, int port, String username, String password) {
        // 默认RTSP端口是554，如果port是8000（SDK端口），则使用554
        int rtspPort = (port == 8000) ? 554 : port;
        
        if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
            // URL编码用户名和密码，避免特殊字符问题
            String encodedUsername = java.net.URLEncoder.encode(username, java.nio.charset.StandardCharsets.UTF_8);
            String encodedPassword = java.net.URLEncoder.encode(password, java.nio.charset.StandardCharsets.UTF_8);
            return "rtsp://" + encodedUsername + ":" + encodedPassword + "@" + ip + ":" + rtspPort + "/Streaming/Channels/101";
        } else {
            return generateRtspUrl(ip, port);
        }
    }

    /**
     * 设备快照/抓图
     * POST /api/devices/:id/snapshot
     */
    public String captureSnapshot(Request request, Response response) {
        try {
            String deviceId = request.params(":id");
            DeviceInfo device = deviceManager.getDevice(deviceId);
            
            if (device == null) {
                response.status(404);
                return createErrorResponse(404, "设备不存在");
            }
            
            // 确保设备已登录
            int channel = device.getChannel() > 0 ? device.getChannel() : 1;
            
            // 使用CaptureService进行抓图
            String picFilePath = captureService.captureSnapshot(deviceId, channel);
            
            if (picFilePath == null) {
                response.status(500);
                return createErrorResponse(500, "抓图失败");
            }
            
            // 返回文件URL（相对路径，前端可以通过代理访问）
            Map<String, Object> data = new HashMap<>();
            data.put("url", "/api/devices/" + deviceId + "/snapshot/file?path=" + java.net.URLEncoder.encode(picFilePath, "UTF-8"));
            data.put("filePath", picFilePath);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
            data.put("timestamp", sdf.format(new Date()));
            
            response.status(200);
            response.type("application/json");
            return createSuccessResponse(data);
        } catch (Exception e) {
            logger.error("抓图失败", e);
            response.status(500);
            return createErrorResponse(500, "抓图失败: " + e.getMessage());
        }
    }

    /**
     * 获取快照文件
     * GET /api/devices/:id/snapshot/file?path=...
     */
    public Object getSnapshotFile(Request request, Response response) {
        try {
            String path = request.queryParams("path");
            if (path == null || path.isEmpty()) {
                response.status(400);
                return createErrorResponse(400, "路径参数不能为空");
            }
            
            // 安全检查：确保路径在captures目录下
            java.io.File file = new java.io.File(path);
            if (!file.exists() || !file.getCanonicalPath().startsWith(new java.io.File("./storage/captures").getCanonicalPath())) {
                response.status(404);
                return createErrorResponse(404, "文件不存在");
            }
            
            response.status(200);
            response.type("image/jpeg");
            response.header("Content-Disposition", "inline; filename=\"" + file.getName() + "\"");
            
            // 读取文件并返回
            java.nio.file.Files.copy(file.toPath(), response.raw().getOutputStream());
            return "";
        } catch (Exception e) {
            logger.error("获取快照文件失败", e);
            response.status(500);
            return createErrorResponse(500, "获取快照文件失败: " + e.getMessage());
        }
    }

    /**
     * PTZ控制
     * POST /api/devices/:id/ptz
     */
    public String ptzControl(Request request, Response response) {
        try {
            String deviceId = request.params(":id");
            DeviceInfo device = deviceManager.getDevice(deviceId);
            
            if (device == null) {
                response.status(404);
                return createErrorResponse(404, "设备不存在");
            }
            
            Map<String, Object> body = objectMapper.readValue(request.body(), Map.class);
            String action = (String) body.get("action"); // start/stop
            String command = (String) body.get("command"); // up/down/left/right/zoom_in/zoom_out
            int speed = body.get("speed") != null ? ((Number) body.get("speed")).intValue() : 1;
            
            if (action == null || command == null) {
                response.status(400);
                return createErrorResponse(400, "action和command参数不能为空");
            }
            
            int channel = device.getChannel() > 0 ? device.getChannel() : 1;
            
            // 使用PTZService进行云台控制
            boolean result = ptzService.ptzControl(deviceId, channel, command, action, speed);
            
            if (!result) {
                response.status(500);
                return createErrorResponse(500, "PTZ控制失败");
            }
            
            Map<String, Object> data = new HashMap<>();
            data.put("message", "PTZ控制命令已执行");
            data.put("action", action);
            data.put("command", command);
            data.put("speed", speed);
            
            response.status(200);
            response.type("application/json");
            return createSuccessResponse(data);
        } catch (Exception e) {
            logger.error("PTZ控制失败", e);
            response.status(500);
            return createErrorResponse(500, "PTZ控制失败: " + e.getMessage());
        }
    }

    /**
     * 获取视频流地址（返回最新录制的视频）
     * GET /api/devices/:id/stream
     */
    public String getStreamUrl(Request request, Response response) {
        try {
            String deviceId = request.params(":id");
            DeviceInfo device = deviceManager.getDevice(deviceId);
            
            if (device == null) {
                response.status(404);
                return createErrorResponse(404, "设备不存在");
            }
            
            // 从请求头中获取token（用于video标签访问）
            String token = getTokenFromRequest(request);
            
            // 返回录制视频URL（包含token参数）
            String videoUrl = getLatestRecordVideo(deviceId, token);
            if (videoUrl == null) {
                response.status(404);
                return createErrorResponse(404, "暂无录制视频");
            }
            
            Map<String, Object> data = new HashMap<>();
            data.put("videoUrl", videoUrl);
            data.put("streamType", "mp4");
            data.put("message", "返回最新录制的视频文件");
            
            response.status(200);
            response.type("application/json");
            return createSuccessResponse(data);
        } catch (Exception e) {
            logger.error("获取视频流地址失败", e);
            response.status(500);
            return createErrorResponse(500, "获取视频流地址失败: " + e.getMessage());
        }
    }

    /**
     * 从请求中获取JWT token
     */
    private String getTokenFromRequest(Request request) {
        String authHeader = request.headers("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    /**
     * 获取设备的最新录制视频文件
     * GET /api/devices/:id/record-video
     */
    public String getRecordVideo(Request request, Response response) {
        try {
            String deviceId = request.params(":id");
            DeviceInfo device = deviceManager.getDevice(deviceId);
            
            if (device == null) {
                response.status(404);
                return createErrorResponse(404, "设备不存在");
            }
            
            // 从请求头中获取token（用于video标签访问）
            String token = getTokenFromRequest(request);
            
            String videoUrl = getLatestRecordVideo(deviceId, token);
            if (videoUrl == null) {
                response.status(404);
                return createErrorResponse(404, "暂无录制视频");
            }
            
            Map<String, Object> data = new HashMap<>();
            data.put("videoUrl", videoUrl);
            data.put("deviceId", deviceId);
            data.put("message", "返回最新录制的视频文件");
            
            response.status(200);
            response.type("application/json");
            return createSuccessResponse(data);
        } catch (Exception e) {
            logger.error("获取录制视频失败", e);
            response.status(500);
            return createErrorResponse(500, "获取录制视频失败: " + e.getMessage());
        }
    }

    /**
     * 获取设备的最新录制视频文件URL（只返回已完成的文件，确保浏览器可以播放）
     * 注意：正在录制的文件可能缺少moov atom，导致浏览器无法解析，所以只返回已完成的文件
     * @param deviceId 设备ID
     * @param token JWT token（已废弃，视频文件访问已免token验证）
     */
    private String getLatestRecordVideo(String deviceId, String token) {
        if (recorder == null) {
            return null;
        }
        
        try {
            // 不返回正在录制的文件（可能缺少moov atom），只返回已完成的文件
            // 获取当前时间前2分钟的视频（确保文件已完全写入）
            Date targetTime = new Date(System.currentTimeMillis() - 120000); // 2分钟前
            String filePath = recorder.getRecordFile(deviceId, targetTime);
            
            // 如果找不到，尝试获取最新的已完成文件
            if (filePath == null) {
                File recordDir = new File("./storage/records");
                if (!recordDir.exists()) {
                    return null;
                }
                
                // 文件名可能包含冒号或下划线，所以需要匹配两种格式
                // 支持MP4格式
                String deviceIdForFile = deviceId.replace(".", "_");
                String prefix1 = "record_" + deviceIdForFile;  // 保留冒号：record_192_168_1_100:8000
                String prefix2 = "record_" + deviceIdForFile.replace(":", "_");  // 替换冒号：record_192_168_1_100_8000
                File[] files = recordDir.listFiles((dir, name) -> {
                    boolean matchesPrefix = name.startsWith(prefix1) || name.startsWith(prefix2);
                    return matchesPrefix && name.endsWith(".mp4");
                });
                
                if (files == null || files.length == 0) {
                    return null;
                }
                
                if (files == null || files.length == 0) {
                    return null;
                }
                
                // 按修改时间排序，获取最新的文件
                Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
                
                // 排除正在录制的文件
                String currentRecordingFile = recorder.getCurrentRecordingFile(deviceId);
                for (File file : files) {
                    // 如果文件不是当前正在录制的文件，且文件大小稳定（最近30秒没有变化），则返回
                    if (currentRecordingFile == null || !file.getAbsolutePath().equals(currentRecordingFile)) {
                        // 检查文件是否稳定（最近30秒没有修改）
                        long lastModified = file.lastModified();
                        long now = System.currentTimeMillis();
                        if (now - lastModified > 30000) { // 文件30秒前已停止写入
                            filePath = file.getAbsolutePath();
                            break;
                        }
                    }
                }
                
                // 如果所有文件都在录制中，返回最新的文件（即使可能不完整）
                if (filePath == null && files.length > 0) {
                    filePath = files[0].getAbsolutePath();
                }
            }
            
            // 转换为HTTP访问URL（视频文件访问已免token验证）
            // 文件路径格式：./storage/records/record_192_168_1_100_8000_20260106154930.mp4
            // URL格式：/api/devices/:id/video?file=xxx.mp4
            File file = new File(filePath);
            if (file.exists()) {
                String fileName = file.getName();
                return "/api/devices/" + java.net.URLEncoder.encode(deviceId, "UTF-8") + "/video?file=" + 
                       java.net.URLEncoder.encode(fileName, "UTF-8");
            }
            
            return null;
        } catch (Exception e) {
            logger.error("获取录制视频文件失败: {}", deviceId, e);
            return null;
        }
    }

    /**
     * 提供视频文件HTTP访问
     * GET /api/devices/:id/video?file=xxx.mp4
     */
    public Object getVideoFile(Request request, Response response) {
        try {
            // URL解码设备ID（Spark会自动解码，但为了安全起见，我们再次解码）
            String deviceId = request.params(":id");
            try {
                deviceId = java.net.URLDecoder.decode(deviceId, "UTF-8");
            } catch (Exception e) {
                // 如果解码失败，使用原始值
                logger.debug("设备ID解码失败，使用原始值: {}", deviceId);
            }
            
            // URL解码文件名
            String fileName = request.queryParams("file");
            if (fileName != null && !fileName.isEmpty()) {
                try {
                    fileName = java.net.URLDecoder.decode(fileName, "UTF-8");
                } catch (Exception e) {
                    // 如果解码失败，使用原始值
                    logger.debug("文件名解码失败，使用原始值: {}", fileName);
                }
            }
            
            if (fileName == null || fileName.isEmpty()) {
                response.status(400);
                return createErrorResponse(400, "文件名参数不能为空");
            }
            
            logger.debug("视频文件请求 - deviceId: {}, fileName: {}", deviceId, fileName);
            
            // 安全检查：防止路径遍历攻击
            if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
                response.status(400);
                return createErrorResponse(400, "无效的文件名");
            }
            
            // 验证文件名格式
            // 设备ID格式：192.168.1.100:8000
            // 文件名格式：record_192_168_1_100:8000_20260106081136.mp4（只替换点号为下划线，冒号保留）
            // 或者：record_192_168_1_100_8000_20260106081136.mp4（点号和冒号都替换为下划线）
            String deviceIdForFile = deviceId.replace(".", "_");
            String expectedPrefix1 = "record_" + deviceIdForFile + "_";  // 保留冒号
            String expectedPrefix2 = "record_" + deviceIdForFile.replace(":", "_") + "_";  // 替换冒号
            String expectedPrefix3 = "extract_" + deviceIdForFile.replace(":", "_") + "_";  // 提取文件
            boolean validPrefix = fileName.startsWith(expectedPrefix1) || fileName.startsWith(expectedPrefix2) || fileName.startsWith(expectedPrefix3);
            boolean validExtension = fileName.endsWith(".mp4");
            if (!validPrefix || !validExtension) {
                response.status(400);
                return createErrorResponse(400, "文件不属于该设备");
            }
            
            // 确定文件路径（提取文件在extracts子目录）
            String filePath = fileName.startsWith("extract_") ? "./storage/records/extracts/" + fileName : "./storage/records/" + fileName;
            File videoFile = new File(filePath);
            if (!videoFile.exists() || !videoFile.isFile()) {
                response.status(404);
                return createErrorResponse(404, "视频文件不存在");
            }
            
            // 检查文件是否正在写入（可能是当前正在录制的文件）
            boolean isRecording = recorder != null && recorder.getCurrentRecordingFile(deviceId) != null && 
                                 recorder.getCurrentRecordingFile(deviceId).equals(videoFile.getAbsolutePath());
            
            // 设置响应头（必须在写入输出流之前设置）
            // 根据文件扩展名设置Content-Type
            String contentType = "video/mp4";
            response.raw().setContentType(contentType);
            response.raw().setHeader("Accept-Ranges", "bytes");
            
            // 如果文件正在录制中，不设置Content-Length，允许流式传输
            // 这样可以支持实时播放正在写入的MP4文件
            if (!isRecording) {
                response.raw().setContentLengthLong(videoFile.length());
            } else {
                // 对于正在录制的文件，使用Transfer-Encoding: chunked
                response.raw().setHeader("Transfer-Encoding", "chunked");
                // 移除Content-Length，允许流式传输
            }
            
            // 支持Range请求（视频拖拽）
            String rangeHeader = request.headers("Range");
            logger.debug("视频文件请求 - deviceId: {}, fileName: {}, Range: {}, isRecording: {}", 
                deviceId, fileName, rangeHeader, isRecording);
            if (rangeHeader != null && rangeHeader.startsWith("bytes=") && !isRecording) {
                // 只有在文件未在录制时才支持Range请求
                logger.debug("处理Range请求: {}", rangeHeader);
                return handleRangeRequest(videoFile, rangeHeader, response);
            }
            
            // 对于所有文件都使用流式传输（避免大文件一次性读取到内存）
            // 对于正在录制的文件，使用特殊的流式读取（支持实时追加）
            if (isRecording) {
                return streamVideoFile(videoFile, response);
            } else {
                // 对于已完成的文件，使用普通流式传输
                return streamCompletedVideoFile(videoFile, response);
            }
            
        } catch (Exception e) {
            logger.error("获取视频文件失败", e);
            response.status(500);
            return createErrorResponse(500, "获取视频文件失败: " + e.getMessage());
        }
    }

    /**
     * 流式传输已完成的视频文件（非录制中）
     * 注意：使用流式传输，确保正确设置Content-Length
     */
    private Object streamCompletedVideoFile(File videoFile, Response response) {
        try {
            logger.debug("开始流式传输视频文件: {}, 大小: {} bytes", videoFile.getName(), videoFile.length());
            
            // 设置响应头（必须在写入输出流之前设置）
            // 使用response.type()和response.header()确保不会被CORS过滤器覆盖
            response.type("video/mp4");
            response.header("Content-Length", String.valueOf(videoFile.length()));
            response.header("Accept-Ranges", "bytes");
            response.raw().setStatus(200);
            
            // 使用流式传输，避免一次性加载到内存
            try (java.io.FileInputStream fis = new java.io.FileInputStream(videoFile);
                 java.io.OutputStream os = response.raw().getOutputStream()) {
                byte[] buffer = new byte[8192]; // 8KB缓冲区
                int bytesRead;
                long totalBytes = 0;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                    totalBytes += bytesRead;
                }
                os.flush();
                logger.debug("视频文件传输完成: {} bytes", totalBytes);
            }
            
            return "";
        } catch (java.io.IOException e) {
            // 客户端断开连接是正常的，不需要记录为错误
            if (e.getMessage() != null && (e.getMessage().contains("Broken pipe") || e.getMessage().contains("Connection reset"))) {
                logger.debug("客户端断开连接: {}", videoFile.getName());
            } else {
                logger.error("流式传输视频文件失败: {}", videoFile.getName(), e);
            }
            try {
                response.raw().getOutputStream().close();
            } catch (Exception ex) {
                // Ignore
            }
            return "";
        } catch (Exception e) {
            logger.error("流式传输视频文件失败: {}", videoFile.getName(), e);
            try {
                response.raw().getOutputStream().close();
            } catch (Exception ex) {
                // Ignore
            }
            response.status(500);
            return createErrorResponse(500, "流式传输视频文件失败: " + e.getMessage());
        }
    }

    /**
     * 流式传输正在录制的视频文件（支持实时播放）
     */
    private Object streamVideoFile(File videoFile, Response response) {
        try {
            // 使用流式读取，每次读取一定大小的数据块
            // 这样可以支持播放正在写入的MP4文件
            java.io.FileInputStream fis = new java.io.FileInputStream(videoFile);
            java.io.OutputStream os = response.raw().getOutputStream();
            
            byte[] buffer = new byte[8192]; // 8KB缓冲区
            int bytesRead;
            long lastSize = videoFile.length();
            int noDataCount = 0; // 连续无数据次数
            
            // 流式传输：持续读取文件直到没有新数据
            // 对于正在录制的文件，会持续读取新写入的数据
            while (noDataCount < 10) { // 最多等待10次（约1秒）
                bytesRead = fis.read(buffer);
                if (bytesRead > 0) {
                    os.write(buffer, 0, bytesRead);
                    os.flush();
                    noDataCount = 0; // 重置计数
                } else {
                    // 没有更多数据，检查文件是否有新数据写入
                    long currentSize = videoFile.length();
                    if (currentSize > lastSize) {
                        // 文件有新数据，继续读取
                        lastSize = currentSize;
                        noDataCount = 0;
                    } else {
                        // 文件大小没有变化，等待新数据
                        noDataCount++;
                        Thread.sleep(100); // 等待100ms后继续检查
                    }
                }
            }
            
            fis.close();
            return "";
        } catch (Exception e) {
            logger.error("流式传输视频文件失败", e);
            try {
                response.raw().getOutputStream().close();
            } catch (Exception ex) {
                // Ignore
            }
            return "";
        }
    }

    /**
     * 处理Range请求（支持视频拖拽）
     */
    private Object handleRangeRequest(File videoFile, String rangeHeader, Response response) {
        try {
            long fileSize = videoFile.length();
            String range = rangeHeader.substring(6); // 移除 "bytes="
            String[] ranges = range.split("-");
            
            long start = 0;
            long end = fileSize - 1;
            
            if (ranges.length > 0 && !ranges[0].isEmpty()) {
                start = Long.parseLong(ranges[0]);
            }
            if (ranges.length > 1 && !ranges[1].isEmpty()) {
                end = Long.parseLong(ranges[1]);
            }
            
            if (start > end || start < 0 || end >= fileSize) {
                response.status(416);
                return "";
            }
            
            long contentLength = end - start + 1;
            response.status(206);
            response.header("Content-Range", String.format("bytes %d-%d/%d", start, end, fileSize));
            response.header("Content-Length", String.valueOf(contentLength));
            
            // 读取文件片段
            byte[] buffer = new byte[(int) contentLength];
            try (java.io.FileInputStream fis = new java.io.FileInputStream(videoFile)) {
                fis.skip(start);
                fis.read(buffer);
            }
            
            return buffer;
        } catch (Exception e) {
            logger.error("处理Range请求失败", e);
            response.status(500);
            return createErrorResponse(500, "处理Range请求失败: " + e.getMessage());
        }
    }


    /**
     * 录像回放
     * POST /api/devices/:id/playback
     */
    public String playback(Request request, Response response) {
        try {
            String deviceId = request.params(":id");
            DeviceInfo device = deviceManager.getDevice(deviceId);
            
            if (device == null) {
                response.status(404);
                return createErrorResponse(404, "设备不存在");
            }
            
            Map<String, Object> body = objectMapper.readValue(request.body(), Map.class);
            String startTimeStr = (String) body.get("startTime");
            String endTimeStr = (String) body.get("endTime");
            int channel = body.get("channel") != null ? ((Number) body.get("channel")).intValue() : device.getChannel();
            
            if (startTimeStr == null || endTimeStr == null) {
                response.status(400);
                return createErrorResponse(400, "startTime和endTime参数不能为空");
            }
            
            // 解析时间
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date startTime = sdf.parse(startTimeStr);
            Date endTime = sdf.parse(endTimeStr);
            
            // 验证时间范围：所有设备限制1小时
            long timeDiff = endTime.getTime() - startTime.getTime();
            long oneHourInMillis = 60 * 60 * 1000; // 1小时的毫秒数
            if (timeDiff > oneHourInMillis) {
                response.status(400);
                return createErrorResponse(400, "设备录像回放时间范围不能超过1小时");
            }
            
            if (timeDiff <= 0) {
                response.status(400);
                return createErrorResponse(400, "结束时间必须晚于开始时间");
            }
            
            // 确保设备已登录
            if (!deviceManager.isDeviceLoggedIn(deviceId)) {
                if (!deviceManager.loginDevice(device)) {
                    response.status(500);
                    return createErrorResponse(500, "设备登录失败，无法启动录像下载");
                }
            }
            
            // 获取设备SDK
            DeviceSDK sdk = deviceManager.getDeviceSDK(deviceId);
            if (sdk == null) {
                response.status(500);
                return createErrorResponse(500, "无法获取设备SDK");
            }
            
            int userId = deviceManager.getDeviceUserId(deviceId);
            if (userId < 0) {
                response.status(500);
                return createErrorResponse(500, "设备未登录");
            }
            
            // 创建下载目录
            String downloadDir = "./storage/downloads";
            File downloadDirFile = new File(downloadDir);
            if (!downloadDirFile.exists()) {
                downloadDirFile.mkdirs();
            }
            
            // 生成本地保存文件路径
            // 根据设备品牌选择文件扩展名：天地伟业使用.sdv，其他使用.mp4
            String fileExtension = "tiandy".equalsIgnoreCase(device.getBrand()) ? ".sdv" : ".mp4";
            String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
            String localFilePath = downloadDir + "/playback_" + deviceId.replaceAll("[^a-zA-Z0-9]", "_") + "_" + timestamp + fileExtension;
            
            // 调用SDK启动下载（使用主码流，streamType=0）
            int downloadHandle = sdk.downloadPlaybackByTimeRange(userId, channel, startTime, endTime, localFilePath, 0);
            
            if (downloadHandle < 0) {
                logger.error("启动录像下载失败: deviceId={}, channel={}, startTime={}, endTime={}", 
                    deviceId, channel, startTimeStr, endTimeStr);
                response.status(500);
                return createErrorResponse(500, "启动录像下载失败，请检查设备连接和参数");
            }
            
            logger.info("录像下载启动成功: deviceId={}, channel={}, downloadHandle={}, filePath={}", 
                deviceId, channel, downloadHandle, localFilePath);
            
            // 返回下载句柄和文件路径
            Map<String, Object> data = new HashMap<>();
            data.put("downloadHandle", downloadHandle);
            data.put("filePath", localFilePath);
            data.put("channel", channel);
            data.put("startTime", startTimeStr);
            data.put("endTime", endTimeStr);
            data.put("message", "录像下载已启动");
            
            response.status(200);
            response.type("application/json");
            return createSuccessResponse(data);
        } catch (java.text.ParseException e) {
            logger.error("时间解析失败", e);
            response.status(400);
            return createErrorResponse(400, "时间格式错误，请使用 yyyy-MM-dd HH:mm:ss 格式");
        } catch (Exception e) {
            logger.error("启动录像下载失败", e);
            response.status(500);
            return createErrorResponse(500, "启动录像下载失败: " + e.getMessage());
        }
    }

    /**
     * 查询录像下载进度
     * GET /api/devices/:id/playback/progress?downloadHandle=xxx
     */
    public String getPlaybackProgress(Request request, Response response) {
        try {
            String deviceId = request.params(":id");
            String downloadHandleStr = request.queryParams("downloadHandle");
            
            if (downloadHandleStr == null || downloadHandleStr.isEmpty()) {
                response.status(400);
                return createErrorResponse(400, "downloadHandle参数不能为空");
            }
            
            int downloadHandle;
            try {
                downloadHandle = Integer.parseInt(downloadHandleStr);
            } catch (NumberFormatException e) {
                response.status(400);
                return createErrorResponse(400, "downloadHandle参数格式错误");
            }
            
            // 获取设备SDK（支持所有品牌）
            DeviceSDK deviceSDK = deviceManager.getDeviceSDK(deviceId);
            if (deviceSDK == null) {
                response.status(500);
                return createErrorResponse(500, "无法获取设备SDK");
            }
            
            // 使用DeviceSDK接口查询下载进度（支持所有品牌）
            int progress = deviceSDK.getDownloadProgress(downloadHandle);
            
            Map<String, Object> data = new HashMap<>();
            data.put("downloadHandle", downloadHandle);
            data.put("progress", progress >= 0 ? progress : 0);
            data.put("isCompleted", progress >= 100);
            data.put("isError", progress < 0);
            
            response.status(200);
            response.type("application/json");
            return createSuccessResponse(data);
        } catch (Exception e) {
            logger.error("查询下载进度失败", e);
            response.status(500);
            return createErrorResponse(500, "查询下载进度失败: " + e.getMessage());
        }
    }

    /**
     * 获取已下载的录像文件
     * GET /api/devices/:id/playback/file?filePath=xxx
     */
    public Object getPlaybackFile(Request request, Response response) {
        try {
            String deviceId = request.params(":id");
            String filePath = request.queryParams("filePath");
            
            if (filePath == null || filePath.isEmpty()) {
                response.status(400);
                return createErrorResponse(400, "filePath参数不能为空");
            }
            
            // URL解码
            try {
                filePath = java.net.URLDecoder.decode(filePath, "UTF-8");
            } catch (Exception e) {
                logger.debug("文件路径解码失败，使用原始值: {}", filePath);
            }
            
            java.io.File file = new java.io.File(filePath);
            
            // 安全检查：确保文件在downloads目录下
            if (!file.getCanonicalPath().startsWith(new java.io.File("./storage/downloads").getCanonicalPath())) {
                response.status(403);
                return createErrorResponse(403, "访问被拒绝：文件不在允许的目录中");
            }
            
            if (!file.exists()) {
                response.status(404);
                return createErrorResponse(404, "录像文件不存在");
            }
            
            // 根据文件扩展名设置正确的Content-Type
            String fileName = file.getName().toLowerCase();
            String contentType;
            if (fileName.endsWith(".mp4")) {
                contentType = "video/mp4";
            } else if (fileName.endsWith(".sdv")) {
                // SDV是天地伟业的私有格式，尝试使用通用视频类型或application/octet-stream
                // 如果浏览器不支持，可能需要转换格式
                contentType = "video/mp4";  // 暂时使用mp4，实际可能需要转换
            } else if (fileName.endsWith(".avi")) {
                contentType = "video/x-msvideo";
            } else if (fileName.endsWith(".mkv")) {
                contentType = "video/x-matroska";
            } else {
                contentType = "video/mp4";  // 默认使用mp4
            }
            
            response.type(contentType);
            response.header("Content-Disposition", "inline; filename=\"" + file.getName() + "\"");
            response.raw().setContentLengthLong(file.length());
            
            // 支持Range请求（视频拖拽）
            String rangeHeader = request.headers("Range");
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                return handleRangeRequest(file, rangeHeader, response);
            }
            
            // 流式传输文件
            return streamCompletedVideoFile(file, response);
            
        } catch (Exception e) {
            logger.error("获取录像文件失败", e);
            response.status(500);
            return createErrorResponse(500, "获取录像文件失败: " + e.getMessage());
        }
    }

    /**
     * 停止录像下载
     * POST /api/devices/:id/playback/stop?downloadHandle=xxx
     */
    public String stopPlayback(Request request, Response response) {
        try {
            String deviceId = request.params(":id");
            String downloadHandleStr = request.queryParams("downloadHandle");
            
            if (downloadHandleStr == null || downloadHandleStr.isEmpty()) {
                response.status(400);
                return createErrorResponse(400, "downloadHandle参数不能为空");
            }
            
            int downloadHandle;
            try {
                downloadHandle = Integer.parseInt(downloadHandleStr);
            } catch (NumberFormatException e) {
                response.status(400);
                return createErrorResponse(400, "downloadHandle参数格式错误");
            }
            
            HCNetSDK hcNetSDK = sdk.getSDK();
            if (hcNetSDK == null) {
                response.status(500);
                return createErrorResponse(500, "SDK未初始化");
            }
            
            // 停止下载
            boolean result = hcNetSDK.NET_DVR_StopGetFile(downloadHandle);
            
            Map<String, Object> data = new HashMap<>();
            data.put("downloadHandle", downloadHandle);
            data.put("stopped", result);
            data.put("message", result ? "下载已停止" : "停止下载失败");
            
            response.status(200);
            response.type("application/json");
            return createSuccessResponse(data);
        } catch (Exception e) {
            logger.error("停止下载失败", e);
            response.status(500);
            return createErrorResponse(500, "停止下载失败: " + e.getMessage());
        }
    }

    /**
     * 导出录像
     * POST /api/devices/:id/export
     */
    public String exportVideo(Request request, Response response) {
        try {
            String deviceId = request.params(":id");
            DeviceInfo device = deviceManager.getDevice(deviceId);
            
            if (device == null) {
                response.status(404);
                return createErrorResponse(404, "设备不存在");
            }
            
            Map<String, Object> body = objectMapper.readValue(request.body(), Map.class);
            String filePath = (String) body.get("filePath");
            
            if (filePath == null || filePath.isEmpty()) {
                response.status(400);
                return createErrorResponse(400, "filePath参数不能为空");
            }
            
            // 安全检查：确保文件在downloads目录下
            java.io.File file = new java.io.File(filePath);
            if (!file.exists()) {
                response.status(404);
                return createErrorResponse(404, "文件不存在");
            }
            
            if (!file.getCanonicalPath().startsWith(new java.io.File("./storage/downloads").getCanonicalPath())) {
                response.status(403);
                return createErrorResponse(403, "无权访问该文件");
            }
            
            // 返回文件下载URL
            Map<String, Object> data = new HashMap<>();
            data.put("downloadUrl", "/api/devices/" + deviceId + "/export/file?path=" + java.net.URLEncoder.encode(filePath, "UTF-8"));
            data.put("fileName", file.getName());
            data.put("fileSize", file.length());
            
            response.status(200);
            response.type("application/json");
            return createSuccessResponse(data);
        } catch (Exception e) {
            logger.error("导出录像失败", e);
            response.status(500);
            return createErrorResponse(500, "导出录像失败: " + e.getMessage());
        }
    }

    /**
     * 获取导出文件
     * GET /api/devices/:id/export/file?path=...
     */
    public Object getExportFile(Request request, Response response) {
        try {
            String path = request.queryParams("path");
            if (path == null || path.isEmpty()) {
                response.status(400);
                return createErrorResponse(400, "路径参数不能为空");
            }
            
            // 安全检查：确保路径在downloads目录下
            java.io.File file = new java.io.File(path);
            if (!file.exists() || !file.getCanonicalPath().startsWith(new java.io.File("./storage/downloads").getCanonicalPath())) {
                response.status(404);
                return createErrorResponse(404, "文件不存在");
            }
            
            response.status(200);
            response.type("video/mp4");
            response.header("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");
            
            // 读取文件并返回
            java.nio.file.Files.copy(file.toPath(), response.raw().getOutputStream());
            return "";
        } catch (Exception e) {
            logger.error("获取导出文件失败", e);
            response.status(500);
            return createErrorResponse(500, "获取导出文件失败: " + e.getMessage());
        }
    }

    /**
     * 转换Date为NET_DVR_TIME
     */
    private HCNetSDK.NET_DVR_TIME convertToDvrTime(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        
        HCNetSDK.NET_DVR_TIME dvrTime = new HCNetSDK.NET_DVR_TIME();
        dvrTime.dwYear = cal.get(Calendar.YEAR);
        dvrTime.dwMonth = cal.get(Calendar.MONTH) + 1;
        dvrTime.dwDay = cal.get(Calendar.DAY_OF_MONTH);
        dvrTime.dwHour = cal.get(Calendar.HOUR_OF_DAY);
        dvrTime.dwMinute = cal.get(Calendar.MINUTE);
        dvrTime.dwSecond = cal.get(Calendar.SECOND);
        dvrTime.write();
        
        return dvrTime;
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
     * 获取支持的品牌列表
     * GET /api/devices/brands
     */
    public String getBrands(Request request, Response response) {
        try {
            Map<String, Object> brands = new HashMap<>();
            brands.put("supported", Arrays.asList(
                DeviceInfo.BRAND_HIKVISION,
                DeviceInfo.BRAND_TIANDY,
                DeviceInfo.BRAND_DAHUA,
                DeviceInfo.BRAND_AUTO
            ));
            brands.put("default", DeviceInfo.BRAND_AUTO);
            
            response.status(200);
            response.type("application/json");
            return createSuccessResponse(brands);
        } catch (Exception e) {
            logger.error("获取品牌列表失败", e);
            response.status(500);
            return createErrorResponse(500, "获取品牌列表失败: " + e.getMessage());
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
