package com.hikvision.nvr.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hikvision.nvr.device.DeviceManager;
import com.hikvision.nvr.database.DeviceInfo;
import com.hikvision.nvr.hikvision.HCNetSDK;
import com.hikvision.nvr.hikvision.HikvisionSDK;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 命令处理器
 * 处理MQTT接收到的各种命令
 */
public class CommandHandler {
    private static final Logger logger = LoggerFactory.getLogger(CommandHandler.class);
    private DeviceManager deviceManager;
    private HikvisionSDK sdk;
    private ObjectMapper objectMapper = new ObjectMapper();

    public CommandHandler(DeviceManager deviceManager, HikvisionSDK sdk) {
        this.deviceManager = deviceManager;
        this.sdk = sdk;
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
            HCNetSDK hcNetSDK = sdk.getSDK();
            if (hcNetSDK == null) {
                return createErrorResponse(requestId, deviceId, "capture", "SDK未初始化");
            }

            // 注意：抓图需要先启动预览，这里简化处理
            // 实际实现需要先调用NET_DVR_RealPlay_V30启动预览，然后调用NET_DVR_CapturePicture
            // 这里先返回占位响应
            Map<String, Object> captureData = new HashMap<>();
            captureData.put("message", "抓图功能需要先启动预览，待完善实现");
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

            // 注意：重启功能需要查找正确的SDK函数
            // 这里先返回占位响应
            Map<String, Object> rebootData = new HashMap<>();
            rebootData.put("message", "重启功能待实现，需要查找正确的SDK函数");
            return createSuccessResponse(requestId, deviceId, "reboot", rebootData);
        } catch (Exception e) {
            logger.error("重启失败", e);
            return createErrorResponse(requestId, deviceId, "reboot", "重启异常: " + e.getMessage());
        }
    }

    /**
     * 处理回放命令
     */
    private CommandResponse handlePlayback(int userId, String deviceId, String requestId, Map<String, Object> command) {
        // 回放功能需要更复杂的实现，这里先返回占位响应
        Map<String, Object> data = new HashMap<>();
        data.put("message", "回放功能待实现");
        return createSuccessResponse(requestId, deviceId, "playback", data);
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
            HCNetSDK hcNetSDK = sdk.getSDK();
            if (hcNetSDK == null) {
                return createErrorResponse(requestId, deviceId, "ptz_control", "SDK未初始化");
            }

            String action = (String) command.get("action");
            int speed = command.get("speed") != null ? ((Number) command.get("speed")).intValue() : 5;
            int channel = command.get("channel") != null ? ((Number) command.get("channel")).intValue() : 1;

            if (action == null) {
                return createErrorResponse(requestId, deviceId, "ptz_control", "动作参数不能为空");
            }

            int commandCode = getPtzCommandCode(action);
            if (commandCode == 0) {
                return createErrorResponse(requestId, deviceId, "ptz_control", "未知的云台动作: " + action);
            }

            // 执行云台控制（使用NET_DVR_PTZControl_Other，需要userId和channel）
            boolean result = hcNetSDK.NET_DVR_PTZControl_Other(userId, channel, commandCode, 0);
            
            if (result) {
                Map<String, Object> data = new HashMap<>();
                data.put("action", action);
                data.put("speed", speed);
                data.put("channel", channel);
                return createSuccessResponse(requestId, deviceId, "ptz_control", data);
            } else {
                int errorCode = sdk.getLastError();
                return createErrorResponse(requestId, deviceId, "ptz_control", "云台控制失败，错误码: " + errorCode);
            }
        } catch (Exception e) {
            logger.error("云台控制失败", e);
            return createErrorResponse(requestId, deviceId, "ptz_control", "云台控制异常: " + e.getMessage());
        }
    }

    /**
     * 获取云台控制命令码
     */
    private int getPtzCommandCode(String action) {
        switch (action.toLowerCase()) {
            case "up":
            case "tilt_up":
                return HCNetSDK.PAN_TILT_UP;
            case "down":
            case "tilt_down":
                return HCNetSDK.PAN_TILT_DOWN;
            case "left":
            case "pan_left":
                return HCNetSDK.PAN_LEFT;
            case "right":
            case "pan_right":
                return HCNetSDK.PAN_RIGHT;
            case "zoom_in":
                return HCNetSDK.ZOOM_IN;
            case "zoom_out":
                return HCNetSDK.ZOOM_OUT;
            default:
                return 0;
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
