package com.hikvision.nvr.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hikvision.nvr.device.DeviceManager;
import com.hikvision.nvr.device.DeviceSDK;
import com.hikvision.nvr.database.DeviceInfo;
import com.hikvision.nvr.hikvision.DeviceStorageChecker;
import com.hikvision.nvr.hikvision.HCNetSDK;
import com.hikvision.nvr.hikvision.HikvisionSDK;
import com.hikvision.nvr.recorder.Recorder;
import com.hikvision.nvr.service.CaptureService;
import com.hikvision.nvr.service.PTZService;
import com.hikvision.nvr.service.PlaybackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 命令处理器
 * 处理MQTT接收到的各种命令
 * 使用功能服务类实现多品牌SDK支持
 */
public class CommandHandler {
    private static final Logger logger = LoggerFactory.getLogger(CommandHandler.class);
    private DeviceManager deviceManager;
    private HikvisionSDK sdk; // 保留用于重启等特殊功能（仅海康设备）
    private Recorder recorder;
    private CaptureService captureService;
    private PTZService ptzService;
    private PlaybackService playbackService;
    private ObjectMapper objectMapper = new ObjectMapper();

    public CommandHandler(DeviceManager deviceManager, HikvisionSDK sdk, Recorder recorder,
                         CaptureService captureService, PTZService ptzService, PlaybackService playbackService) {
        this.deviceManager = deviceManager;
        this.sdk = sdk; // 保留用于重启等特殊功能
        this.recorder = recorder;
        this.captureService = captureService;
        this.ptzService = ptzService;
        this.playbackService = playbackService;
    }

    /**
     * 处理命令
     */
    public CommandResponse handleCommand(String commandJson) {
        try {
            Map<String, Object> command = objectMapper.readValue(commandJson, Map.class);
            String commandType = (String) command.get("command");
            String deviceId = (String) command.get("device_id");
            String requestId = (String) command.get("request_id");

            if (deviceId == null) {
                return createErrorResponse(requestId, deviceId, commandType, "设备ID不能为空");
            }

            DeviceInfo device = deviceManager.getDevice(deviceId);
            if (device == null) {
                return createErrorResponse(requestId, deviceId, commandType, "设备不存在");
            }

            // 确保设备已登录
            if (!deviceManager.isDeviceLoggedIn(deviceId)) {
                if (!deviceManager.loginDevice(device)) {
                    return createErrorResponse(requestId, deviceId, commandType, "设备登录失败");
                }
            }

            int userId = deviceManager.getDeviceUserId(deviceId);
            CommandResponse response;

            switch (commandType) {
                case "capture":
                    response = handleCapture(userId, deviceId, requestId);
                    break;
                case "reboot":
                    response = handleReboot(userId, deviceId, requestId);
                    break;
                case "playback":
                    response = handlePlayback(userId, deviceId, requestId, command);
                    break;
                case "play_audio":
                    response = handlePlayAudio(userId, deviceId, requestId, command);
                    break;
                case "ptz_control":
                    response = handlePtzControl(userId, deviceId, requestId, command);
                    break;
                default:
                    response = createErrorResponse(requestId, deviceId, commandType, "未知命令: " + commandType);
            }

            return response;

        } catch (Exception e) {
            logger.error("处理命令失败", e);
            return createErrorResponse(null, null, null, "命令处理异常: " + e.getMessage());
        }
    }

    /**
     * 处理抓图命令
     */
    private CommandResponse handleCapture(int userId, String deviceId, String requestId) {
        try {
            // 获取设备信息以获取通道号
            DeviceInfo device = deviceManager.getDevice(deviceId);
            if (device == null) {
                return createErrorResponse(requestId, deviceId, "capture", "设备不存在");
            }

            // 获取通道号（默认使用通道1，如果设备信息中有通道号则使用设备的）
            int channel = device.getChannel() > 0 ? device.getChannel() : 1;

            // 使用CaptureService进行抓图
            String picFilePath = captureService.captureSnapshot(deviceId, channel);
            if (picFilePath == null) {
                return createErrorResponse(requestId, deviceId, "capture", "抓图失败");
            }

            // 读取图片文件并转换为base64
            File picFile = new File(picFilePath);
            if (!picFile.exists()) {
                return createErrorResponse(requestId, deviceId, "capture", "抓图文件未生成");
            }

            byte[] imageBytes = Files.readAllBytes(Paths.get(picFilePath));
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            // 构建响应数据
            Map<String, Object> captureData = new HashMap<>();
            captureData.put("image_base64", base64Image);
            captureData.put("image_size", imageBytes.length);
            captureData.put("channel", channel);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
            captureData.put("timestamp", sdf.format(new Date()));

            logger.info("抓图成功: 设备={}, 通道={}, 文件大小={}字节", deviceId, channel, imageBytes.length);

            return createSuccessResponse(requestId, deviceId, "capture", captureData);

        } catch (Exception e) {
            logger.error("抓图失败", e);
            return createErrorResponse(requestId, deviceId, "capture", "抓图异常: " + e.getMessage());
        }
    }

    /**
     * 处理重启命令
     */
    private CommandResponse handleReboot(int userId, String deviceId, String requestId) {
        try {
            HCNetSDK hcNetSDK = sdk.getSDK();
            if (hcNetSDK == null) {
                return createErrorResponse(requestId, deviceId, "reboot", "SDK未初始化");
            }

            // 使用NET_DVR_RemoteControl进行远程重启
            // MINOR_REMOTE_REBOOT = 0x7b 表示远程重启命令
            boolean result = hcNetSDK.NET_DVR_RemoteControl(userId, HCNetSDK.MINOR_REMOTE_REBOOT, null, 0);

            if (!result) {
                int errorCode = sdk.getLastError();
                logger.error("重启设备失败，错误码: {}", errorCode);
                return createErrorResponse(requestId, deviceId, "reboot", "重启设备失败，错误码: " + errorCode);
            }

            Map<String, Object> rebootData = new HashMap<>();
            rebootData.put("message", "设备重启命令已发送");
            rebootData.put("device_id", deviceId);
            logger.info("设备重启命令已发送: {}", deviceId);

            return createSuccessResponse(requestId, deviceId, "reboot", rebootData);

        } catch (Exception e) {
            logger.error("重启失败", e);
            return createErrorResponse(requestId, deviceId, "reboot", "重启异常: " + e.getMessage());
        }
    }

    /**
     * 处理回放命令（查询回放文件列表）
     * 优先使用本地录制文件，如果没有则从设备查询
     */
    private CommandResponse handlePlayback(int userId, String deviceId, String requestId, Map<String, Object> command) {
        try {
            // 获取设备信息
            DeviceInfo device = deviceManager.getDevice(deviceId);
            if (device == null) {
                return createErrorResponse(requestId, deviceId, "playback", "设备不存在");
            }

            // 获取参数（如果未提供时间，则使用当前时间前1分钟作为目标时间）
            String startTimeStr = (String) command.get("start_time");
            String endTimeStr = (String) command.get("end_time");
            int channel = command.get("channel") != null ? ((Number) command.get("channel")).intValue() : device.getChannel();

            // 如果没有提供时间，使用当前时间前1分钟（前后30秒，共1分钟）
            Date startTime;
            Date endTime;
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            
            if (startTimeStr == null || endTimeStr == null) {
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.MINUTE, -1); // 当前时间前1分钟
                
                // 计算前后30秒的时间范围
                cal.add(Calendar.SECOND, -30);
                startTime = cal.getTime();
                cal.add(Calendar.SECOND, 60);
                endTime = cal.getTime();
                
                startTimeStr = sdf.format(startTime);
                endTimeStr = sdf.format(endTime);
            } else {
                // 解析提供的时间
                startTime = sdf.parse(startTimeStr);
                endTime = sdf.parse(endTimeStr);
            }

            // 优先尝试从本地录制文件获取
            if (recorder != null) {
                String localFile = recorder.getRecordFile(deviceId, startTime);
                if (localFile != null) {
                    File file = new File(localFile);
                    if (file.exists() && file.length() > 0) {
                        try {
                            // 读取本地文件并转换为base64
                            byte[] videoBytes = Files.readAllBytes(Paths.get(localFile));
                            String base64Video = Base64.getEncoder().encodeToString(videoBytes);

                            Map<String, Object> playbackData = new HashMap<>();
                            playbackData.put("source", "local");
                            playbackData.put("file_path", localFile);
                            playbackData.put("file_size", videoBytes.length);
                            playbackData.put("video_base64", base64Video);
                            playbackData.put("channel", channel);
                            playbackData.put("start_time", startTimeStr);
                            playbackData.put("end_time", endTimeStr);

                            logger.info("从本地录制文件获取回放: 设备={}, 文件={}, 大小={}字节", 
                                deviceId, localFile, videoBytes.length);
                            return createSuccessResponse(requestId, deviceId, "playback", playbackData);
                        } catch (Exception e) {
                            logger.warn("读取本地录制文件失败，尝试从设备查询: {}", localFile, e);
                        }
                    }
                }
            }

            // 本地文件不存在，使用PlaybackService查询设备上的文件列表
            List<DeviceSDK.PlaybackFile> files = playbackService.queryPlaybackFiles(deviceId, channel, startTime, endTime);
            
            // 转换为响应格式
            List<Map<String, Object>> fileList = new ArrayList<>();
            for (DeviceSDK.PlaybackFile file : files) {
                Map<String, Object> fileInfo = new HashMap<>();
                fileInfo.put("fileName", file.getFileName());
                fileInfo.put("startTime", sdf.format(file.getStartTime()));
                fileInfo.put("endTime", sdf.format(file.getEndTime()));
                fileInfo.put("fileSize", file.getFileSize());
                fileInfo.put("channel", file.getChannel());
                fileList.add(fileInfo);
            }

            Map<String, Object> playbackData = new HashMap<>();
            playbackData.put("source", "device");
            playbackData.put("files", fileList);
            playbackData.put("count", fileList.size());
            playbackData.put("channel", channel);
            playbackData.put("start_time", startTimeStr);
            playbackData.put("end_time", endTimeStr);

            logger.info("查询回放文件列表: 设备={}, 通道={}, 文件数={}", deviceId, channel, fileList.size());

            return createSuccessResponse(requestId, deviceId, "playback", playbackData);

        } catch (Exception e) {
            logger.error("回放查询失败", e);
            return createErrorResponse(requestId, deviceId, "playback", "回放查询异常: " + e.getMessage());
        }
    }

    /**
     * 处理播放声音命令
     */
    private CommandResponse handlePlayAudio(int userId, String deviceId, String requestId, Map<String, Object> command) {
        // 播放声音功能需要更复杂的实现，这里先返回占位响应
        Map<String, Object> data = new HashMap<>();
        data.put("message", "播放声音功能待实现");
        return createSuccessResponse(requestId, deviceId, "play_audio", data);
    }

    /**
     * 处理云台控制命令
     */
    private CommandResponse handlePtzControl(int userId, String deviceId, String requestId, Map<String, Object> command) {
        try {
            DeviceInfo device = deviceManager.getDevice(deviceId);
            if (device == null) {
                return createErrorResponse(requestId, deviceId, "ptz_control", "设备不存在");
            }

            String action = (String) command.get("action");
            String commandStr = (String) command.get("command"); // 支持command参数
            if (commandStr == null) {
                commandStr = action; // 如果没有command，使用action
            }
            int speed = command.get("speed") != null ? ((Number) command.get("speed")).intValue() : 5;
            int channel = command.get("channel") != null ? ((Number) command.get("channel")).intValue() : device.getChannel();

            if (action == null || commandStr == null) {
                return createErrorResponse(requestId, deviceId, "ptz_control", "动作参数不能为空");
            }

            // 使用PTZService进行云台控制
            boolean result = ptzService.ptzControl(deviceId, channel, commandStr, action, speed);
            
            if (result) {
                Map<String, Object> data = new HashMap<>();
                data.put("action", action);
                data.put("command", commandStr);
                data.put("speed", speed);
                data.put("channel", channel);
                return createSuccessResponse(requestId, deviceId, "ptz_control", data);
            } else {
                return createErrorResponse(requestId, deviceId, "ptz_control", "云台控制失败");
            }
        } catch (Exception e) {
            logger.error("云台控制失败", e);
            return createErrorResponse(requestId, deviceId, "ptz_control", "云台控制异常: " + e.getMessage());
        }
    }

    /**
     * 创建成功响应
     */
    private CommandResponse createSuccessResponse(String requestId, String deviceId, String command, Map<String, Object> data) {
        CommandResponse response = new CommandResponse();
        response.setRequestId(requestId);
        response.setDeviceId(deviceId);
        response.setCommand(command);
        response.setSuccess(true);
        response.setData(data);
        response.setError("");
        return response;
    }

    /**
     * 创建错误响应
     */
    private CommandResponse createErrorResponse(String requestId, String deviceId, String command, String error) {
        CommandResponse response = new CommandResponse();
        response.setRequestId(requestId);
        response.setDeviceId(deviceId);
        response.setCommand(command);
        response.setSuccess(false);
        response.setData(new HashMap<>());
        response.setError(error);
        return response;
    }
}
