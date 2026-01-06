package com.hikvision.nvr.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hikvision.nvr.database.DeviceInfo;
import com.hikvision.nvr.device.DeviceManager;
import com.hikvision.nvr.hikvision.HCNetSDK;
import com.hikvision.nvr.hikvision.HikvisionSDK;
import com.hikvision.nvr.recorder.Recorder;
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
 */
public class DeviceController {
    private static final Logger logger = LoggerFactory.getLogger(DeviceController.class);
    private final DeviceManager deviceManager;
    private final HikvisionSDK sdk;
    private final com.hikvision.nvr.database.Database database;
    private final Recorder recorder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DeviceController(DeviceManager deviceManager, HikvisionSDK sdk, com.hikvision.nvr.database.Database database, Recorder recorder) {
        this.deviceManager = deviceManager;
        this.sdk = sdk;
        this.database = database;
        this.recorder = recorder;
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
            if (!deviceManager.isDeviceLoggedIn(deviceId)) {
                if (!deviceManager.loginDevice(device)) {
                    response.status(500);
                    return createErrorResponse(500, "设备登录失败，无法抓图");
                }
            }
            
            int userId = deviceManager.getDeviceUserId(deviceId);
            int channel = device.getChannel() > 0 ? device.getChannel() : 1;
            HCNetSDK hcNetSDK = sdk.getSDK();
            
            if (hcNetSDK == null) {
                response.status(500);
                return createErrorResponse(500, "SDK未初始化");
            }
            
            // 设置抓图参数
            HCNetSDK.NET_DVR_JPEGPARA jpegPara = new HCNetSDK.NET_DVR_JPEGPARA();
            jpegPara.wPicSize = 0; // 0=CIF, 使用当前分辨率
            jpegPara.wPicQuality = 2; // 图片质量：0-最好 1-较好 2-一般
            jpegPara.write();
            
            // 创建临时文件保存图片
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
            String timestamp = sdf.format(new Date());
            String picDir = "./captures";
            java.io.File dir = new java.io.File(picDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            String picFileName = picDir + "/capture_" + deviceId.replace(".", "_").replace(":", "_") + "_" + timestamp + ".jpg";
            byte[] fileNameBytes = picFileName.getBytes("UTF-8");
            byte[] fileNameArray = new byte[256];
            System.arraycopy(fileNameBytes, 0, fileNameArray, 0, Math.min(fileNameBytes.length, fileNameArray.length - 1));
            
            // 执行抓图
            boolean captureResult = hcNetSDK.NET_DVR_CaptureJPEGPicture(userId, channel, jpegPara, fileNameArray);
            
            if (!captureResult) {
                int errorCode = sdk.getLastError();
                response.status(500);
                return createErrorResponse(500, "抓图失败，错误码: " + errorCode);
            }
            
            // 等待文件写入完成
            Thread.sleep(1000);
            
            // 检查文件是否存在
            java.io.File picFile = new java.io.File(picFileName);
            if (!picFile.exists()) {
                response.status(500);
                return createErrorResponse(500, "抓图文件未生成");
            }
            
            // 返回文件URL（相对路径，前端可以通过代理访问）
            Map<String, Object> data = new HashMap<>();
            data.put("url", "/api/devices/" + deviceId + "/snapshot/file?path=" + java.net.URLEncoder.encode(picFileName, "UTF-8"));
            data.put("filePath", picFileName);
            data.put("timestamp", timestamp);
            
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
            if (!file.exists() || !file.getCanonicalPath().startsWith(new java.io.File("./captures").getCanonicalPath())) {
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
            
            // 确保设备已登录
            if (!deviceManager.isDeviceLoggedIn(deviceId)) {
                if (!deviceManager.loginDevice(device)) {
                    response.status(500);
                    return createErrorResponse(500, "设备登录失败，无法控制PTZ");
                }
            }
            
            Map<String, Object> body = objectMapper.readValue(request.body(), Map.class);
            String action = (String) body.get("action"); // start/stop
            String command = (String) body.get("command"); // up/down/left/right/zoom_in/zoom_out
            int speed = body.get("speed") != null ? ((Number) body.get("speed")).intValue() : 1;
            
            if (action == null || command == null) {
                response.status(400);
                return createErrorResponse(400, "action和command参数不能为空");
            }
            
            int userId = deviceManager.getDeviceUserId(deviceId);
            int channel = device.getChannel() > 0 ? device.getChannel() : 1;
            HCNetSDK hcNetSDK = sdk.getSDK();
            
            if (hcNetSDK == null) {
                response.status(500);
                return createErrorResponse(500, "SDK未初始化");
            }
            
            // 获取PTZ命令码
            int commandCode = getPtzCommandCode(command);
            if (commandCode == -1) {
                response.status(400);
                return createErrorResponse(400, "未知的PTZ命令: " + command);
            }
            
            // 执行PTZ控制
            // action: start=0, stop=1
            int stopFlag = "stop".equalsIgnoreCase(action) ? 1 : 0;
            boolean result = hcNetSDK.NET_DVR_PTZControl_Other(userId, channel, commandCode, stopFlag);
            
            if (!result) {
                int errorCode = sdk.getLastError();
                response.status(500);
                return createErrorResponse(500, "PTZ控制失败，错误码: " + errorCode);
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
     * 获取PTZ命令码
     */
    private int getPtzCommandCode(String command) {
        switch (command.toLowerCase()) {
            case "up":
                return HCNetSDK.TILT_UP;
            case "down":
                return HCNetSDK.TILT_DOWN;
            case "left":
                return HCNetSDK.PAN_LEFT;
            case "right":
                return HCNetSDK.PAN_RIGHT;
            case "zoom_in":
                return HCNetSDK.ZOOM_IN;
            case "zoom_out":
                return HCNetSDK.ZOOM_OUT;
            default:
                return -1;
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
            
            // 返回录制视频URL
            String videoUrl = getLatestRecordVideo(deviceId);
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
            
            String videoUrl = getLatestRecordVideo(deviceId);
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
     * 获取设备的最新录制视频文件URL
     */
    private String getLatestRecordVideo(String deviceId) {
        if (recorder == null) {
            return null;
        }
        
        try {
            // 获取当前时间前1分钟的视频（最近录制的视频）
            Date targetTime = new Date(System.currentTimeMillis() - 60000);
            String filePath = recorder.getRecordFile(deviceId, targetTime);
            
            if (filePath == null) {
                // 如果找不到，尝试获取最新的录制文件
                File recordDir = new File("./records");
                if (!recordDir.exists()) {
                    return null;
                }
                
                File[] files = recordDir.listFiles((dir, name) -> 
                    name.startsWith("record_" + deviceId.replace(".", "_")) && name.endsWith(".mp4"));
                
                if (files == null || files.length == 0) {
                    return null;
                }
                
                // 按修改时间排序，获取最新的文件
                Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
                filePath = files[0].getAbsolutePath();
            }
            
            // 转换为HTTP访问URL
            // 文件路径格式：./records/record_192_168_1_100_8000_20260106154930.mp4
            // URL格式：/api/devices/:id/video?file=record_192_168_1_100_8000_20260106154930.mp4
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
            String deviceId = request.params(":id");
            String fileName = request.queryParams("file");
            
            if (fileName == null || fileName.isEmpty()) {
                response.status(400);
                return createErrorResponse(400, "文件名参数不能为空");
            }
            
            // 安全检查：防止路径遍历攻击
            if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
                response.status(400);
                return createErrorResponse(400, "无效的文件名");
            }
            
            // 验证文件名格式
            if (!fileName.startsWith("record_" + deviceId.replace(".", "_")) || !fileName.endsWith(".mp4")) {
                response.status(400);
                return createErrorResponse(400, "文件不属于该设备");
            }
            
            File videoFile = new File("./records/" + fileName);
            if (!videoFile.exists() || !videoFile.isFile()) {
                response.status(404);
                return createErrorResponse(404, "视频文件不存在");
            }
            
            // 设置响应头
            response.type("video/mp4");
            response.header("Accept-Ranges", "bytes");
            response.header("Content-Length", String.valueOf(videoFile.length()));
            
            // 支持Range请求（视频拖拽）
            String rangeHeader = request.headers("Range");
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                return handleRangeRequest(videoFile, rangeHeader, response);
            }
            
            // 返回完整文件
            byte[] fileBytes = Files.readAllBytes(videoFile.toPath());
            return fileBytes;
            
        } catch (Exception e) {
            logger.error("获取视频文件失败", e);
            response.status(500);
            return createErrorResponse(500, "获取视频文件失败: " + e.getMessage());
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
            
            // 确保设备已登录
            if (!deviceManager.isDeviceLoggedIn(deviceId)) {
                if (!deviceManager.loginDevice(device)) {
                    response.status(500);
                    return createErrorResponse(500, "设备登录失败，无法回放");
                }
            }
            
            Map<String, Object> body = objectMapper.readValue(request.body(), Map.class);
            String startTimeStr = (String) body.get("startTime");
            String endTimeStr = (String) body.get("endTime");
            int channel = body.get("channel") != null ? ((Number) body.get("channel")).intValue() : device.getChannel();
            
            if (startTimeStr == null || endTimeStr == null) {
                response.status(400);
                return createErrorResponse(400, "startTime和endTime参数不能为空");
            }
            
            int userId = deviceManager.getDeviceUserId(deviceId);
            HCNetSDK hcNetSDK = sdk.getSDK();
            
            if (hcNetSDK == null) {
                response.status(500);
                return createErrorResponse(500, "SDK未初始化");
            }
            
            // 解析时间
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date startTime = sdf.parse(startTimeStr);
            Date endTime = sdf.parse(endTimeStr);
            
            // 设置回放条件
            HCNetSDK.NET_DVR_TIME startTimeStruct = convertToDvrTime(startTime);
            HCNetSDK.NET_DVR_TIME endTimeStruct = convertToDvrTime(endTime);
            
            HCNetSDK.NET_DVR_PLAYCOND playCond = new HCNetSDK.NET_DVR_PLAYCOND();
            playCond.dwChannel = channel;
            playCond.struStartTime = startTimeStruct;
            playCond.struStopTime = endTimeStruct;
            playCond.write();
            
            // 生成下载文件名
            SimpleDateFormat fileSdf = new SimpleDateFormat("yyyyMMddHHmmss");
            String timestamp = fileSdf.format(startTime);
            String downloadDir = "./downloads";
            java.io.File dir = new java.io.File(downloadDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            String fileName = downloadDir + "/playback_" + deviceId.replace(".", "_").replace(":", "_") + "_" + timestamp + ".mp4";
            
            // 开始下载
            int downloadHandle = hcNetSDK.NET_DVR_GetFileByTime_V40(userId, fileName, playCond);
            if (downloadHandle == -1) {
                int errorCode = sdk.getLastError();
                response.status(500);
                return createErrorResponse(500, "开始下载录像失败，错误码: " + errorCode);
            }
            
            // 启动下载
            boolean playResult = hcNetSDK.NET_DVR_PlayBackControl(downloadHandle, HCNetSDK.NET_DVR_PLAYSTART, 0, null);
            if (!playResult) {
                int errorCode = sdk.getLastError();
                response.status(500);
                return createErrorResponse(500, "启动下载失败，错误码: " + errorCode);
            }
            
            Map<String, Object> data = new HashMap<>();
            data.put("downloadHandle", downloadHandle);
            data.put("filePath", fileName);
            data.put("channel", channel);
            data.put("startTime", startTimeStr);
            data.put("endTime", endTimeStr);
            data.put("message", "录像下载已启动，请使用downloadHandle查询下载进度");
            
            response.status(200);
            response.type("application/json");
            return createSuccessResponse(data);
        } catch (Exception e) {
            logger.error("录像回放失败", e);
            response.status(500);
            return createErrorResponse(500, "录像回放失败: " + e.getMessage());
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
            
            if (!file.getCanonicalPath().startsWith(new java.io.File("./downloads").getCanonicalPath())) {
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
            if (!file.exists() || !file.getCanonicalPath().startsWith(new java.io.File("./downloads").getCanonicalPath())) {
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
