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
import com.digital.video.gateway.service.PtzMonitorService;
import com.digital.video.gateway.service.PlaybackService;
import com.digital.video.gateway.service.ZlmProxyService;
import com.digital.video.gateway.service.AssemblyService;
import com.digital.video.gateway.service.AlarmRuleService;
import com.digital.video.gateway.database.DevicePtzExtensionTable;
import com.digital.video.gateway.database.Assembly;
import com.digital.video.gateway.database.AlarmRule;
import com.digital.video.gateway.database.AssemblyDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.RandomAccessFile;
import java.io.IOException;
import java.text.SimpleDateFormat;
import spark.Request;
import spark.Response;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 设备管理控制器
 * 使用功能服务类实现多品牌SDK支持
 */
public class DeviceController {
    private static final Logger logger = LoggerFactory.getLogger(DeviceController.class);
    /** 录像下载句柄 -> 本地文件路径，用于进度查询时按文件是否就绪兜底判定完成，并返回 filePath */
    private final Map<String, String> playbackHandleToPath = new ConcurrentHashMap<>();
    private final DeviceManager deviceManager;
    private final com.digital.video.gateway.database.Database database;
    private final Recorder recorder;
    private final CaptureService captureService;
    private final PTZService ptzService;
    private final PtzMonitorService ptzMonitorService;
    private final PlaybackService playbackService;
    private final HikvisionSDK sdk; // 保留用于重启等特殊功能（仅海康设备）
    private final AssemblyService assemblyService;
    private final AlarmRuleService alarmRuleService;
    private final ZlmProxyService zlmProxyService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DeviceController(DeviceManager deviceManager, com.digital.video.gateway.database.Database database,
            Recorder recorder, CaptureService captureService, PTZService ptzService,
            PtzMonitorService ptzMonitorService,
            PlaybackService playbackService, HikvisionSDK sdk,
            AssemblyService assemblyService, AlarmRuleService alarmRuleService,
            ZlmProxyService zlmProxyService) {
        this.deviceManager = deviceManager;
        this.database = database;
        this.recorder = recorder;
        this.captureService = captureService;
        this.ptzService = ptzService;
        this.ptzMonitorService = ptzMonitorService;
        this.playbackService = playbackService;
        this.sdk = sdk; // 保留用于重启等特殊功能
        this.assemblyService = assemblyService;
        this.alarmRuleService = alarmRuleService;
        this.zlmProxyService = zlmProxyService;
    }

    /**
     * 建议一个可用的设备国标 20 位 ID（供前端「自动生成」按钮使用，不在后台自动赋值）
     * GET /api/devices/suggest-gb-id
     */
    public String suggestGbId(Request request, Response response) {
        try {
            String suggested = database.suggestDeviceGbId();
            Map<String, Object> data = new HashMap<>();
            data.put("suggested_gb_id", suggested);
            response.type("application/json");
            return objectMapper.writeValueAsString(Map.of("code", 0, "data", data));
        } catch (Exception e) {
            logger.error("建议国标 ID 失败", e);
            response.status(500);
            return createErrorResponse(500, "建议国标 ID 失败: " + e.getMessage());
        }
    }

    /**
     * 用户主动设置设备国标 ID（将当前 device_id 更新为 20 位国标 ID，并同步所有关联表）
     * PUT /api/devices/:id/set-gb-id   body: { "gb_id": "34017101041320000001" }
     */
    public String setDeviceGbId(Request request, Response response) {
        try {
            String deviceId = request.params(":id");
            if (deviceId == null || deviceId.isEmpty()) {
                response.status(400);
                return createErrorResponse(400, "缺少设备 ID");
            }
            Map<String, Object> body = objectMapper.readValue(request.body(), Map.class);
            String gbId = (String) body.get("gb_id");
            if (gbId == null || gbId.isEmpty()) {
                response.status(400);
                return createErrorResponse(400, "缺少 gb_id");
            }
            DeviceInfo device = deviceManager.getDevice(deviceId);
            if (device == null) {
                response.status(404);
                return createErrorResponse(404, "设备不存在");
            }
            if (!database.setDeviceGbId(deviceId, gbId.trim())) {
                response.status(400);
                return createErrorResponse(400, "设置国标 ID 失败（格式须为 20 位数字或该 ID 已存在）");
            }
            DeviceInfo updated = deviceManager.getDevice(gbId.trim());
            response.type("application/json");
            return createSuccessResponse(convertDeviceToMap(updated));
        } catch (Exception e) {
            logger.error("设置设备国标 ID 失败", e);
            response.status(500);
            return createErrorResponse(500, "设置国标 ID 失败: " + e.getMessage());
        }
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
                    String deviceStatusStr = device.getStatus() == 1 ? "online" : "offline";
                    if (!deviceStatusStr.equalsIgnoreCase(statusFilter)) {
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

            String ip = (String) body.get("ip");
            int port = ((Number) body.get("port")).intValue();
            DeviceInfo existingDevice = database.getDeviceByIpPort(ip, port);

            DeviceInfo device = new DeviceInfo();
            if (existingDevice != null) {
                device.setDeviceId(existingDevice.getDeviceId());
                device.setId(existingDevice.getId());
            } else {
                // 新设备使用虚拟 ID，国标 ID 由用户在前端主动设置
                device.setDeviceId("v_" + java.util.UUID.randomUUID().toString().replace("-", ""));
            }
            device.setIp(ip);
            device.setPort(port);
            device.setName((String) body.get("name"));
            device.setUsername((String) body.getOrDefault("username", "admin"));
            device.setPassword((String) body.getOrDefault("password", ""));
            device.setStatus(0);
            device.setChannel(((Number) body.getOrDefault("channel", 1)).intValue());
            // 设置品牌，如果未指定则使用auto（自动检测）
            String brand = (String) body.getOrDefault("brand", DeviceInfo.BRAND_AUTO);
            device.setBrand(brand != null ? brand : DeviceInfo.BRAND_AUTO);

            // 生成包含认证信息的RTSP URL
            String rtspUrl = generateRtspUrlWithAuth(device.getIp(), device.getPort(), device.getUsername(),
                    device.getPassword());
            device.setRtspUrl(rtspUrl);

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

            // 保存更新前的品牌信息，用于检测品牌变化
            String oldBrand = device.getBrand();
            if (oldBrand == null || oldBrand.isEmpty()) {
                oldBrand = DeviceInfo.BRAND_AUTO;
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
            if (body.containsKey("cameraType")) {
                device.setCameraType((String) body.get("cameraType"));
            }
            if (body.containsKey("serialNumber")) {
                device.setSerialNumber((String) body.get("serialNumber"));
            }

            // 处理品牌变化
            String newBrand = oldBrand;
            boolean brandChanged = false;
            if (body.containsKey("brand")) {
                String brand = (String) body.get("brand");
                if (brand != null && !brand.isEmpty()) {
                    newBrand = brand;
                    device.setBrand(brand);
                    // 检查品牌是否真的发生了变化
                    if (!oldBrand.equals(newBrand)) {
                        brandChanged = true;
                        logger.info("设备品牌已变更: {} ({} -> {})", deviceId, oldBrand, newBrand);
                    }
                }
            }

            // 更新RTSP URL
            String rtspUrl = generateRtspUrlWithAuth(device.getIp(), device.getPort(), device.getUsername(),
                    device.getPassword());
            device.setRtspUrl(rtspUrl);

            // 保存到数据库
            database.saveOrUpdateDevice(device);

            // 如果设备信息发生变化（IP、端口、用户名、密码、品牌），立即触发登录
            boolean needRelogin = body.containsKey("ip") || body.containsKey("port") ||
                    body.containsKey("username") || body.containsKey("password") ||
                    brandChanged;
            
            if (needRelogin) {
                // 如果设备已登录，先登出
                if (deviceManager.isDeviceLoggedIn(deviceId)) {
                    logger.info("设备已登录，先登出: {} (原品牌: {})", deviceId, oldBrand);
                    deviceManager.logoutDevice(deviceId);
                }
                
                // 记录登录尝试的详细信息
                if (brandChanged) {
                    boolean isFromAuto = DeviceInfo.BRAND_AUTO.equals(oldBrand) || 
                                        oldBrand == null || oldBrand.isEmpty();
                    boolean isToSpecific = !DeviceInfo.BRAND_AUTO.equals(newBrand) && 
                                          !newBrand.isEmpty();
                    
                    if (isFromAuto && isToSpecific) {
                        logger.info("设备品牌从自动检测改为具体品牌，立即尝试登录: {} ({} -> {})", 
                                deviceId, oldBrand, newBrand);
                    } else {
                        logger.info("设备品牌已变更，立即尝试重新登录: {} ({} -> {})", 
                                deviceId, oldBrand, newBrand);
                    }
                } else {
                    logger.info("设备信息已更新，立即尝试重新登录: {} (IP/端口/用户名/密码变化)", deviceId);
                }
                
                // 立即触发登录（异步执行，不阻塞API响应）
                deviceManager.loginDevice(device);
            } else if (brandChanged) {
                // 即使其他信息没变，品牌变化也应该触发登录
                logger.info("仅品牌变化，立即尝试登录: {} ({} -> {})", deviceId, oldBrand, newBrand);
                if (deviceManager.isDeviceLoggedIn(deviceId)) {
                    deviceManager.logoutDevice(deviceId);
                }
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
        map.put("status", device.getStatus() == 1 ? "ONLINE" : "OFFLINE");
        map.put("lastSeen", formatLastSeen(device.getLastSeen()));
        map.put("firmware", ""); // 可以从设备信息中获取

        // 如果rtspUrl不包含认证信息，生成包含认证信息的URL
        String rtspUrl = device.getRtspUrl();
        if (rtspUrl == null || rtspUrl.isEmpty() || !rtspUrl.contains("@")) {
            rtspUrl = generateRtspUrlWithAuth(device.getIp(), device.getPort(), device.getUsername(),
                    device.getPassword());
        }
        map.put("rtspUrl", rtspUrl);
        map.put("username", device.getUsername());
        map.put("password", device.getPassword()); // 也返回密码，前端可能需要
        map.put("channel", device.getChannel());
        map.put("cameraType", device.getCameraType() != null ? device.getCameraType() : "other");
        map.put("serialNumber", device.getSerialNumber());
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
            return "rtsp://" + encodedUsername + ":" + encodedPassword + "@" + ip + ":" + rtspPort
                    + "/Streaming/Channels/101";
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
            data.put("url", "/api/devices/" + deviceId + "/snapshot/file?path="
                    + java.net.URLEncoder.encode(picFilePath, "UTF-8"));
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
            if (!file.exists()
                    || !file.getCanonicalPath().startsWith(new java.io.File("./storage/captures").getCanonicalPath())) {
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

            // 智能默认速度逻辑：如果请求中未指定速度，则根据设备品牌设置合理的默认值
            int speed;
            if (body.get("speed") != null) {
                speed = ((Number) body.get("speed")).intValue();
            } else {
                speed = getDefaultSpeed(device.getBrand());
                logger.debug("请求未指定速度，使用品牌 {} 的默认速度: {}", device.getBrand(), speed);
            }

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
     * PTZ绝对定位控制
     * POST /api/devices/:id/ptz/goto
     * 直接指定水平角度、垂直角度、变倍参数控制球机
     */
    public String ptzGoto(Request request, Response response) {
        try {
            String deviceId = request.params(":id");
            DeviceInfo device = deviceManager.getDevice(deviceId);

            if (device == null) {
                response.status(404);
                return createErrorResponse(404, "设备不存在");
            }

            Map<String, Object> body = objectMapper.readValue(request.body(), Map.class);
            
            // 获取PTZ参数
            if (body.get("pan") == null || body.get("tilt") == null) {
                response.status(400);
                return createErrorResponse(400, "pan和tilt参数不能为空");
            }
            
            float pan = ((Number) body.get("pan")).floatValue();   // 水平角度 0-360°
            float tilt = ((Number) body.get("tilt")).floatValue(); // 垂直角度
            float zoom = body.get("zoom") != null ? ((Number) body.get("zoom")).floatValue() : 1.0f; // 变倍，默认1.0

            int channel = device.getChannel() > 0 ? device.getChannel() : 1;

            // 使用PTZService进行绝对定位
            boolean result = ptzService.gotoAngle(deviceId, channel, pan, tilt, zoom);

            if (!result) {
                response.status(500);
                return createErrorResponse(500, "PTZ绝对定位失败");
            }

            Map<String, Object> data = new HashMap<>();
            data.put("message", "云台定位命令已执行");
            data.put("pan", pan);
            data.put("tilt", tilt);
            data.put("zoom", zoom);

            response.status(200);
            response.type("application/json");
            return createSuccessResponse(data);
        } catch (Exception e) {
            logger.error("PTZ绝对定位失败", e);
            response.status(500);
            return createErrorResponse(500, "PTZ绝对定位失败: " + e.getMessage());
        }
    }

    /**
     * 根据设备品牌获取合理的云台默认速度
     */
    private int getDefaultSpeed(String brand) {
        if (brand == null) {
            return 1;
        }

        switch (brand.toLowerCase()) {
            case "tiandy":
                return 50; // 天地伟业范围 0-100，50为中等速度
            case "hikvision":
                return 4; // 海康范围 1-7，4为中等速度
            case "dahua":
                return 4; // 大华通常范围也较小，暂设为4
            default:
                return 1; // 默认保持兼容，使用最小值
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
     * 获取设备直播地址（ZLM 拉流代理 → HTTP-FLV/HLS）
     * GET /api/devices/:id/live/url
     */
    public String getLiveUrl(Request request, Response response) {
        try {
            if (zlmProxyService == null) {
                response.status(503);
                return createErrorResponse(503, "直播服务未启用（请配置 zlm.enabled）");
            }
            String deviceId = request.params(":id");
            String host = request.host() != null ? request.host() : "127.0.0.1";
            if (host.contains(":")) host = host.substring(0, host.indexOf(':'));
            Map<String, String> urls = zlmProxyService.getLiveUrl(deviceId, host);
            if (urls == null) {
                response.status(404);
                return createErrorResponse(404, "设备不存在或无 RTSP 地址");
            }
            Map<String, Object> data = new HashMap<>(urls);
            response.status(200);
            response.type("application/json");
            return createSuccessResponse(data);
        } catch (Exception e) {
            logger.error("获取直播地址失败", e);
            response.status(500);
            return createErrorResponse(500, "获取直播地址失败: " + e.getMessage());
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
     * 
     * @param deviceId 设备ID
     * @param token    JWT token（已废弃，视频文件访问已免token验证）
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
                String prefix1 = "record_" + deviceIdForFile; // 保留冒号：record_192_168_1_100:8000
                String prefix2 = "record_" + deviceIdForFile.replace(":", "_"); // 替换冒号：record_192_168_1_100_8000
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
            String expectedPrefix1 = "record_" + deviceIdForFile + "_"; // 保留冒号
            String expectedPrefix2 = "record_" + deviceIdForFile.replace(":", "_") + "_"; // 替换冒号
            String expectedPrefix3 = "extract_" + deviceIdForFile.replace(":", "_") + "_"; // 提取文件
            boolean validPrefix = fileName.startsWith(expectedPrefix1) || fileName.startsWith(expectedPrefix2)
                    || fileName.startsWith(expectedPrefix3);
            boolean validExtension = fileName.endsWith(".mp4");
            if (!validPrefix || !validExtension) {
                response.status(400);
                return createErrorResponse(400, "文件不属于该设备");
            }

            // 确定文件路径（提取文件在extracts子目录）
            String filePath = fileName.startsWith("extract_") ? "./storage/records/extracts/" + fileName
                    : "./storage/records/" + fileName;
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
            if (e.getMessage() != null
                    && (e.getMessage().contains("Broken pipe") || e.getMessage().contains("Connection reset"))) {
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
                response.header("Content-Range", "bytes */" + fileSize);
                return "";
            }

            long contentLength = end - start + 1;
            response.status(206);
            response.header("Content-Range", String.format("bytes %d-%d/%d", start, end, fileSize));
            response.header("Content-Length", String.valueOf(contentLength));
            response.header("Accept-Ranges", "bytes");
            response.type("video/mp4");

            // 使用RandomAccessFile进行高效的文件片断读取和流式传输
            try (RandomAccessFile raf = new RandomAccessFile(videoFile, "r");
                    java.io.OutputStream os = response.raw().getOutputStream()) {
                raf.seek(start);
                byte[] buffer = new byte[8192];
                long remaining = contentLength;
                while (remaining > 0) {
                    int toRead = (int) Math.min(buffer.length, remaining);
                    int read = raf.read(buffer, 0, toRead);
                    if (read == -1)
                        break;
                    os.write(buffer, 0, read);
                    remaining -= read;
                }
                os.flush();
            }

            return "";
        } catch (Exception e) {
            logger.error("处理Range请求失败", e);
            response.status(500);
            return "";
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

            // 验证时间范围：限制为1分钟分段
            long timeDiff = endTime.getTime() - startTime.getTime();
            long oneMinuteInMillis = 60 * 1000;
            if (timeDiff > oneMinuteInMillis) {
                response.status(400);
                return createErrorResponse(400, "设备录像回放时间范围不能超过1分钟");
            }

            if (timeDiff <= 0) {
                response.status(400);
                return createErrorResponse(400, "结束时间必须晚于开始时间");
            }

            // 按设备分目录 + 以分钟时间命名: ./storage/downloads/{sanitizedDeviceId}/{yyyyMMdd}_{HH}_{mm}.mp4
            String sanitizedDeviceId = deviceId.replaceAll("[^a-zA-Z0-9]", "_");
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTime(startTime);
            String datePart = new SimpleDateFormat("yyyyMMdd").format(startTime);
            int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
            int minute = cal.get(java.util.Calendar.MINUTE);
            String fileName = datePart + "_" + String.format("%02d", hour) + "_" + String.format("%02d", minute) + ".mp4";
            String downloadDir = "./storage/downloads/" + sanitizedDeviceId;
            File downloadDirFile = new File(downloadDir);
            if (!downloadDirFile.exists()) {
                downloadDirFile.mkdirs();
            }
            String localFilePath = downloadDir + "/" + fileName;

            // 缓存命中：文件已存在且大小>0则直接返回
            File cacheFile = new File(localFilePath);
            if (cacheFile.exists() && cacheFile.length() > 0) {
                logger.info("录像缓存命中: deviceId={}, filePath={}", deviceId, localFilePath);
                Map<String, Object> data = new HashMap<>();
                data.put("downloadHandle", -2);
                data.put("filePath", localFilePath);
                data.put("channel", channel);
                data.put("startTime", startTimeStr);
                data.put("endTime", endTimeStr);
                data.put("cached", true);
                data.put("message", "录像已从缓存返回");
                response.status(200);
                response.type("application/json");
                return createSuccessResponse(data);
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

            playbackHandleToPath.put(deviceId + ":" + downloadHandle, localFilePath);

            Map<String, Object> data = new HashMap<>();
            data.put("downloadHandle", downloadHandle);
            data.put("filePath", localFilePath);
            data.put("channel", channel);
            data.put("startTime", startTimeStr);
            data.put("endTime", endTimeStr);
            data.put("cached", false);
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

            // 缓存命中场景：handle=-2 表示文件已就绪，直接返回完成
            if (downloadHandle == -2) {
                Map<String, Object> data = new HashMap<>();
                data.put("downloadHandle", downloadHandle);
                data.put("progress", 100);
                data.put("isCompleted", true);
                data.put("isError", false);
                response.status(200);
                response.type("application/json");
                return createSuccessResponse(data);
            }

            // 获取设备SDK（支持所有品牌）
            DeviceSDK deviceSDK = deviceManager.getDeviceSDK(deviceId);
            if (deviceSDK == null) {
                response.status(500);
                return createErrorResponse(500, "无法获取设备SDK");
            }

            // 使用DeviceSDK接口查询下载进度（支持所有品牌）
            int progress = deviceSDK.getDownloadProgress(downloadHandle);

            // 兜底：若 SDK 未返回 100 但本地文件已存在且大小>0，视为完成（避免轮询永不结束）
            String pathKey = deviceId + ":" + downloadHandle;
            String storedFilePath = playbackHandleToPath.get(pathKey);
            if (storedFilePath != null && progress < 100 && progress >= 0) {
                File f = new File(storedFilePath);
                if (f.exists() && f.length() > 0) {
                    progress = 100;
                    logger.debug("录像下载完成(按文件就绪): deviceId={}, handle={}, filePath={}", deviceId, downloadHandle, storedFilePath);
                }
            }

            boolean isCompleted = progress >= 100;
            Map<String, Object> data = new HashMap<>();
            data.put("downloadHandle", downloadHandle);
            data.put("progress", progress >= 0 ? progress : 0);
            data.put("isCompleted", isCompleted);
            data.put("isError", progress < 0);
            if (isCompleted && storedFilePath != null) {
                data.put("filePath", storedFilePath);
                playbackHandleToPath.remove(pathKey);
            }

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
                contentType = "video/mp4"; // 暂时使用mp4，实际可能需要转换
            } else if (fileName.endsWith(".avi")) {
                contentType = "video/x-msvideo";
            } else if (fileName.endsWith(".mkv")) {
                contentType = "video/x-matroska";
            } else {
                contentType = "video/mp4"; // 默认使用mp4
            }

            response.type(contentType);
            response.header("Content-Disposition", "inline; filename=\"" + file.getName() + "\"");
            response.header("Accept-Ranges", "bytes");

            // 支持Range请求（视频拖拽）
            String rangeHeader = request.headers("Range");
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                return handleRangeRequest(file, rangeHeader, response);
            }

            response.raw().setContentLengthLong(file.length());
            // 流式传输文件
            return streamCompletedVideoFile(file, response);

        } catch (Exception e) {
            logger.error("获取录像文件失败", e);
            response.status(500);
            return createErrorResponse(500, "获取录像文件失败: " + e.getMessage());
        }
    }

    /**
     * 获取回放转码流地址（MP4 → ZLM+FFmpeg → HTTP-FLV，供浏览器播放 H.265 等不兼容编码）
     * GET /api/devices/:id/playback/transcode-url?filePath=xxx
     */
    public String getPlaybackTranscodeUrl(Request request, Response response) {
        try {
            if (zlmProxyService == null) {
                response.status(503);
                return createErrorResponse(503, "转码服务未启用（请配置 zlm.enabled 并安装 FFmpeg）");
            }
            String deviceId = request.params(":id");
            String filePath = request.queryParams("filePath");
            if (filePath == null || filePath.isEmpty()) {
                response.status(400);
                return createErrorResponse(400, "filePath参数不能为空");
            }
            try {
                filePath = java.net.URLDecoder.decode(filePath, "UTF-8");
            } catch (Exception e) {
                logger.debug("transcode-url filePath 解码失败，使用原始值");
            }
            String host = request.host() != null ? request.host() : "127.0.0.1";
            if (host.contains(":")) host = host.substring(0, host.indexOf(':'));
            Map<String, String> result = zlmProxyService.getPlaybackTranscodeUrl(deviceId, filePath, host);
            if (result == null) {
                response.status(400);
                return createErrorResponse(400, "文件无效或转码启动失败");
            }
            Map<String, Object> data = new HashMap<>(result);
            response.status(200);
            response.type("application/json");
            return createSuccessResponse(data);
        } catch (Exception e) {
            logger.error("获取回放转码地址失败", e);
            response.status(500);
            return createErrorResponse(500, "获取回放转码地址失败: " + e.getMessage());
        }
    }

    /**
     * 停止回放转码任务
     * POST /api/devices/:id/playback/transcode-stop  body: {"key":"xxx"}
     */
    public String postPlaybackTranscodeStop(Request request, Response response) {
        try {
            if (zlmProxyService == null) {
                response.status(503);
                return createErrorResponse(503, "转码服务未启用");
            }
            Map<String, Object> body = objectMapper.readValue(request.body(), Map.class);
            String key = (String) body.get("key");
            if (key == null || key.isEmpty()) {
                response.status(400);
                return createErrorResponse(400, "key参数不能为空");
            }
            boolean ok = zlmProxyService.stopPlaybackTranscode(key);
            Map<String, Object> data = new HashMap<>();
            data.put("stopped", ok);
            response.status(200);
            response.type("application/json");
            return createSuccessResponse(data);
        } catch (Exception e) {
            logger.error("停止回放转码失败", e);
            response.status(500);
            return createErrorResponse(500, "停止回放转码失败: " + e.getMessage());
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

            playbackHandleToPath.remove(deviceId + ":" + downloadHandle);

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
            data.put("downloadUrl",
                    "/api/devices/" + deviceId + "/export/file?path=" + java.net.URLEncoder.encode(filePath, "UTF-8"));
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
            if (!file.exists() || !file.getCanonicalPath()
                    .startsWith(new java.io.File("./storage/downloads").getCanonicalPath())) {
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
                    DeviceInfo.BRAND_AUTO));
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

    /**
     * 获取设备配置（装置关联、报警规则等）
     * GET /api/devices/:id/config
     */
    public String getDeviceConfig(Request request, Response response) {
        try {
            String deviceId = request.params(":id");
            DeviceInfo device = deviceManager.getDevice(deviceId);
            if (device == null) {
                response.status(404);
                return createErrorResponse(404, "设备不存在");
            }

            Map<String, Object> data = new HashMap<>();

            // Get assemblies
            List<Assembly> assemblies = assemblyService.getAssembliesByDevice(deviceId);
            data.put("assemblies", assemblies);

            // Get rules
            List<AlarmRule> rules = alarmRuleService.getDeviceRules(deviceId);
            data.put("rules", rules);

            // Get role (from first assembly if exists)
            if (!assemblies.isEmpty()) {
                AssemblyDevice ad = assemblyService.getAssemblyDevice(assemblies.get(0).getAssemblyId(), deviceId);
                if (ad != null) {
                    data.put("role", ad.getDeviceRole());
                }
            }

            response.status(200);
            return createSuccessResponse(data);
        } catch (Exception e) {
            logger.error("获取设备配置失败", e);
            response.status(500);
            return createErrorResponse(500, "获取设备配置失败: " + e.getMessage());
        }
    }

    /**
     * 更新设备配置
     * PUT /api/devices/:id/config
     */
    public String updateDeviceConfig(Request request, Response response) {
        try {
            String deviceId = request.params(":id");
            Map<String, Object> body = objectMapper.readValue(request.body(), Map.class);

            Object assemblyIdObj = body.get("assemblyId");
            String assemblyId = assemblyIdObj != null ? String.valueOf(assemblyIdObj) : null;

            Object roleObj = body.get("role");
            String role = roleObj != null ? String.valueOf(roleObj) : null;

            if (assemblyId != null && role != null) {
                // Check if device is in assembly
                AssemblyDevice ad = assemblyService.getAssemblyDevice(assemblyId, deviceId);
                if (ad != null) {
                    // Update role
                    assemblyService.updateDeviceRole(assemblyId, deviceId, role);
                } else {
                    // Add to assembly
                    assemblyService.addDeviceToAssembly(assemblyId, deviceId, role, null);
                }
            }

            response.status(200);
            return createSuccessResponse(Map.of("message", "配置已更新"));
        } catch (Exception e) {
            logger.error("更新设备配置失败", e);
            response.status(500);
            return createErrorResponse(500, "更新设备配置失败: " + e.getMessage());
        }
    }

    // ========== PTZ位置监控API ==========

    /**
     * 获取设备PTZ位置
     * GET /api/devices/:id/ptz/position
     */
    public String getPtzPosition(Request request, Response response) {
        try {
            String deviceId = request.params(":id");
            DeviceInfo device = deviceManager.getDevice(deviceId);

            if (device == null) {
                response.status(404);
                return createErrorResponse(404, "设备不存在");
            }

            DevicePtzExtensionTable.PtzExtension ptzExt = ptzMonitorService.getPtzPosition(deviceId);
            
            Map<String, Object> data = new HashMap<>();
            if (ptzExt != null) {
                data.put("deviceId", ptzExt.getDeviceId());
                data.put("ptzEnabled", ptzExt.isPtzEnabled());
                data.put("pan", ptzExt.getPan());
                data.put("tilt", ptzExt.getTilt());
                data.put("zoom", ptzExt.getZoom());
                data.put("azimuth", ptzExt.getAzimuth());
                data.put("horizontalFov", ptzExt.getHorizontalFov());
                data.put("verticalFov", ptzExt.getVerticalFov());
                data.put("visibleRadius", ptzExt.getVisibleRadius());
                data.put("lastUpdated", ptzExt.getLastUpdated() != null ? 
                    ptzExt.getLastUpdated().toString() : null);
            } else {
                data.put("deviceId", deviceId);
                data.put("ptzEnabled", false);
                data.put("message", "设备无PTZ位置信息");
            }

            response.status(200);
            response.type("application/json");
            return createSuccessResponse(data);
        } catch (Exception e) {
            logger.error("获取PTZ位置失败", e);
            response.status(500);
            return createErrorResponse(500, "获取PTZ位置失败: " + e.getMessage());
        }
    }

    /**
     * 主动刷新设备PTZ位置
     * POST /api/devices/:id/ptz/refresh
     */
    public String refreshPtzPosition(Request request, Response response) {
        try {
            String deviceId = request.params(":id");
            DeviceInfo device = deviceManager.getDevice(deviceId);

            if (device == null) {
                response.status(404);
                return createErrorResponse(404, "设备不存在");
            }

            // 刷新PTZ位置
            boolean success = ptzMonitorService.refreshPtzPosition(deviceId);
            
            if (!success) {
                response.status(500);
                return createErrorResponse(500, "刷新PTZ位置失败，设备可能不支持或未登录");
            }

            // 返回更新后的位置
            DevicePtzExtensionTable.PtzExtension ptzExt = ptzMonitorService.getPtzPosition(deviceId);
            
            Map<String, Object> data = new HashMap<>();
            data.put("message", "PTZ位置已刷新");
            if (ptzExt != null) {
                data.put("pan", ptzExt.getPan());
                data.put("tilt", ptzExt.getTilt());
                data.put("zoom", ptzExt.getZoom());
            }

            response.status(200);
            response.type("application/json");
            return createSuccessResponse(data);
        } catch (Exception e) {
            logger.error("刷新PTZ位置失败", e);
            response.status(500);
            return createErrorResponse(500, "刷新PTZ位置失败: " + e.getMessage());
        }
    }

    /**
     * 设置设备PTZ监控开关
     * PUT /api/devices/:id/ptz/monitor
     * Body: { "enabled": true }
     */
    public String setPtzMonitor(Request request, Response response) {
        try {
            String deviceId = request.params(":id");
            DeviceInfo device = deviceManager.getDevice(deviceId);

            if (device == null) {
                response.status(404);
                return createErrorResponse(404, "设备不存在");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> body = objectMapper.readValue(request.body(), Map.class);
            
            if (body.get("enabled") == null) {
                response.status(400);
                return createErrorResponse(400, "enabled参数不能为空");
            }
            
            boolean enabled = (Boolean) body.get("enabled");

            // 设置PTZ监控开关
            boolean success = ptzMonitorService.setPtzMonitorEnabled(deviceId, enabled);
            
            if (!success) {
                response.status(500);
                return createErrorResponse(500, "设置PTZ监控状态失败");
            }

            Map<String, Object> data = new HashMap<>();
            data.put("deviceId", deviceId);
            data.put("ptzEnabled", enabled);
            data.put("message", enabled ? "PTZ监控已启用" : "PTZ监控已禁用");

            response.status(200);
            response.type("application/json");
            return createSuccessResponse(data);
        } catch (Exception e) {
            logger.error("设置PTZ监控状态失败", e);
            response.status(500);
            return createErrorResponse(500, "设置PTZ监控状态失败: " + e.getMessage());
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
