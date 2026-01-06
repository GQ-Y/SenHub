package com.hikvision.nvr.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hikvision.nvr.device.DeviceManager;
import com.hikvision.nvr.database.DeviceInfo;
import com.hikvision.nvr.hikvision.DeviceStorageChecker;
import com.hikvision.nvr.hikvision.HCNetSDK;
import com.hikvision.nvr.hikvision.HikvisionSDK;
import com.hikvision.nvr.recorder.Recorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
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
    private Recorder recorder;
    private ObjectMapper objectMapper = new ObjectMapper();

    public CommandHandler(DeviceManager deviceManager, HikvisionSDK sdk, Recorder recorder) {
        this.deviceManager = deviceManager;
        this.sdk = sdk;
        this.recorder = recorder;
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

            // 获取设备信息以获取通道号
            DeviceInfo device = deviceManager.getDevice(deviceId);
            if (device == null) {
                return createErrorResponse(requestId, deviceId, "capture", "设备不存在");
            }

            // 获取通道号（默认使用通道1，如果设备信息中有通道号则使用设备的）
            int channel = device.getChannel() > 0 ? device.getChannel() : 1;

            // 设置抓图参数
            HCNetSDK.NET_DVR_JPEGPARA jpegPara = new HCNetSDK.NET_DVR_JPEGPARA();
            jpegPara.wPicSize = 0; // 0=CIF, 使用当前分辨率
            jpegPara.wPicQuality = 2; // 图片质量：0-最好 1-较好 2-一般
            jpegPara.write();

            // 创建临时文件保存图片（每个设备只保留一张最新图片）
            String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
            String picDir = "./captures";
            File dir = new File(picDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            
            // 删除该设备的所有旧图片（只保留最新一张）
            String deviceIdForFile = deviceId.replace(".", "_").replace(":", "_");
            String prefix = "capture_" + deviceIdForFile + "_";
            File[] oldFiles = dir.listFiles((d, name) -> name.startsWith(prefix) && name.endsWith(".jpg"));
            if (oldFiles != null && oldFiles.length > 0) {
                int deletedCount = 0;
                for (File oldFile : oldFiles) {
                    if (oldFile.delete()) {
                        deletedCount++;
                        logger.debug("已删除旧抓图文件: {}", oldFile.getName());
                    } else {
                        logger.warn("删除旧抓图文件失败: {}", oldFile.getName());
                    }
                }
                if (deletedCount > 0) {
                    logger.info("已删除 {} 个旧抓图文件（设备: {}）", deletedCount, deviceId);
                }
            }
            
            String picFileName = picDir + "/capture_" + deviceIdForFile + "_" + timestamp + ".jpg";
            byte[] fileNameBytes = picFileName.getBytes("UTF-8");
            byte[] fileNameArray = new byte[256];
            System.arraycopy(fileNameBytes, 0, fileNameArray, 0, Math.min(fileNameBytes.length, fileNameArray.length - 1));

            // 执行抓图
            logger.info("开始抓图: 设备={}, 通道={}, 文件={}", deviceId, channel, picFileName);
            boolean result = hcNetSDK.NET_DVR_CaptureJPEGPicture(userId, channel, jpegPara, fileNameArray);

            if (!result) {
                int errorCode = sdk.getLastError();
                logger.error("抓图失败，错误码: {}", errorCode);
                return createErrorResponse(requestId, deviceId, "capture", "抓图失败，错误码: " + errorCode);
            }

            // 等待文件写入完成
            Thread.sleep(500);

            // 读取图片文件并转换为base64
            File picFile = new File(picFileName);
            if (!picFile.exists()) {
                return createErrorResponse(requestId, deviceId, "capture", "抓图文件未生成");
            }

            byte[] imageBytes = Files.readAllBytes(Paths.get(picFileName));
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            // 构建响应数据
            Map<String, Object> captureData = new HashMap<>();
            captureData.put("image_base64", base64Image);
            captureData.put("image_size", imageBytes.length);
            captureData.put("channel", channel);
            captureData.put("timestamp", timestamp);

            logger.info("抓图成功: 设备={}, 通道={}, 文件大小={}字节", deviceId, channel, imageBytes.length);

            // 可选：删除临时文件（如果需要保留则注释掉）
            // picFile.delete();

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
     * 处理回放命令（按时间下载录像）
     * 优先使用本地录制文件，如果没有则从设备下载
     * 时间限制：只能查询当前系统时间前后1分钟（共2分钟）的视频
     */
    private CommandResponse handlePlayback(int userId, String deviceId, String requestId, Map<String, Object> command) {
        try {
            HCNetSDK hcNetSDK = sdk.getSDK();
            if (hcNetSDK == null) {
                return createErrorResponse(requestId, deviceId, "playback", "SDK未初始化");
            }

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
            Date targetTime;
            if (startTimeStr == null || endTimeStr == null) {
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.MINUTE, -1); // 当前时间前1分钟
                targetTime = cal.getTime();
                
                // 计算前后30秒的时间范围
                cal.add(Calendar.SECOND, -30);
                Date startTime = cal.getTime();
                cal.add(Calendar.SECOND, 60);
                Date endTime = cal.getTime();
                
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                startTimeStr = sdf.format(startTime);
                endTimeStr = sdf.format(endTime);
            } else {
                // 解析提供的时间
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                targetTime = sdf.parse(startTimeStr);
            }

            // 验证时间范围：只能查询当前时间前后1分钟（共2分钟）
            Date now = new Date();
            long timeDiff = Math.abs(now.getTime() - targetTime.getTime());
            long maxDiff = 60 * 1000; // 1分钟 = 60秒 = 60000毫秒
            
            if (timeDiff > maxDiff) {
                return createErrorResponse(requestId, deviceId, "playback", 
                    "时间超出范围，只能查询当前系统时间前后1分钟的视频");
            }

            // 优先尝试从本地录制文件获取
            if (recorder != null) {
                String localFile = recorder.getRecordFile(deviceId, targetTime);
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
                            logger.warn("读取本地录制文件失败，尝试从设备下载: {}", localFile, e);
                        }
                    }
                }
            }

            // 本地文件不存在，尝试从设备下载
            // 首先检查设备是否有存储
            if (!DeviceStorageChecker.hasStorage(hcNetSDK, userId)) {
                return createErrorResponse(requestId, deviceId, "playback", "未加载存储介质");
            }

            // 解析时间字符串 "2024-01-01 10:00:00"
            HCNetSDK.NET_DVR_TIME startTime = parseTimeString(startTimeStr);
            HCNetSDK.NET_DVR_TIME endTime = parseTimeString(endTimeStr);

            if (startTime == null || endTime == null) {
                return createErrorResponse(requestId, deviceId, "playback", "时间格式错误，应为: YYYY-MM-DD HH:mm:ss");
            }

            // 设置下载条件
            HCNetSDK.NET_DVR_PLAYCOND playCond = new HCNetSDK.NET_DVR_PLAYCOND();
            playCond.dwChannel = channel; // 通道号
            playCond.struStartTime = startTime;
            playCond.struStopTime = endTime;
            playCond.write();

            // 创建下载目录
            String downloadDir = "./downloads";
            File dir = new File(downloadDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // 生成文件名
            String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
            String fileName = downloadDir + "/playback_" + deviceId.replace(".", "_") + "_" + timestamp + ".mp4";
            byte[] fileNameBytes = fileName.getBytes("UTF-8");
            byte[] fileNameArray = new byte[256];
            System.arraycopy(fileNameBytes, 0, fileNameArray, 0, Math.min(fileNameBytes.length, fileNameArray.length - 1));

            // 开始下载录像
            logger.info("开始下载录像: 设备={}, 通道={}, 时间={} 到 {}", deviceId, channel, startTimeStr, endTimeStr);
            int downloadHandle = hcNetSDK.NET_DVR_GetFileByTime_V40(userId, fileName, playCond);

            if (downloadHandle < 0) {
                int errorCode = sdk.getLastError();
                logger.error("开始下载录像失败，错误码: {}", errorCode);
                return createErrorResponse(requestId, deviceId, "playback", "开始下载录像失败，错误码: " + errorCode);
            }

            // 启动下载
            boolean playResult = hcNetSDK.NET_DVR_PlayBackControl(downloadHandle, HCNetSDK.NET_DVR_PLAYSTART, 0, null);
            if (!playResult) {
                int errorCode = sdk.getLastError();
                logger.error("启动下载失败，错误码: {}", errorCode);
                hcNetSDK.NET_DVR_StopGetFile(downloadHandle);
                return createErrorResponse(requestId, deviceId, "playback", "启动下载失败，错误码: " + errorCode);
            }

            // 构建响应数据
            Map<String, Object> playbackData = new HashMap<>();
            playbackData.put("source", "device");
            playbackData.put("download_handle", downloadHandle);
            playbackData.put("file_path", fileName);
            playbackData.put("channel", channel);
            playbackData.put("start_time", startTimeStr);
            playbackData.put("end_time", endTimeStr);
            playbackData.put("message", "录像下载已启动，请使用download_handle查询下载进度");

            logger.info("从设备下载录像已启动: 设备={}, 句柄={}, 文件={}", deviceId, downloadHandle, fileName);

            // 注意：实际下载是异步的，需要定期查询下载进度
            // 这里返回下载句柄，客户端可以通过其他接口查询下载状态

            return createSuccessResponse(requestId, deviceId, "playback", playbackData);

        } catch (Exception e) {
            logger.error("回放失败", e);
            return createErrorResponse(requestId, deviceId, "playback", "回放异常: " + e.getMessage());
        }
    }

    /**
     * 解析时间字符串为NET_DVR_TIME结构
     * 格式: "2024-01-01 10:00:00"
     */
    private HCNetSDK.NET_DVR_TIME parseTimeString(String timeStr) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date date = sdf.parse(timeStr);

            HCNetSDK.NET_DVR_TIME time = new HCNetSDK.NET_DVR_TIME();
            time.dwYear = Integer.parseInt(new SimpleDateFormat("yyyy").format(date));
            time.dwMonth = Integer.parseInt(new SimpleDateFormat("MM").format(date));
            time.dwDay = Integer.parseInt(new SimpleDateFormat("dd").format(date));
            time.dwHour = Integer.parseInt(new SimpleDateFormat("HH").format(date));
            time.dwMinute = Integer.parseInt(new SimpleDateFormat("mm").format(date));
            time.dwSecond = Integer.parseInt(new SimpleDateFormat("ss").format(date));

            return time;
        } catch (Exception e) {
            logger.error("解析时间字符串失败: {}", timeStr, e);
            return null;
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
                return HCNetSDK.TILT_UP;
            case "down":
            case "tilt_down":
                return HCNetSDK.TILT_DOWN;
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
