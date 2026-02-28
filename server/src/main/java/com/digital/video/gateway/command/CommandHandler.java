package com.digital.video.gateway.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.digital.video.gateway.device.DeviceManager;
import com.digital.video.gateway.device.DeviceSDK;
import com.digital.video.gateway.database.DeviceInfo;
import com.digital.video.gateway.recorder.Recorder;
import com.digital.video.gateway.service.CaptureService;
import com.digital.video.gateway.service.OssService;
import com.digital.video.gateway.service.PTZService;
import com.digital.video.gateway.service.PlaybackService;
import com.digital.video.gateway.service.RecorderService;
import com.digital.video.gateway.service.RecordingTaskService;
import com.digital.video.gateway.database.RecordingTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 命令处理器
 * 处理MQTT接收到的各种命令
 * 使用功能服务类实现多品牌SDK支持（海康/天地伟业/大华）
 */
public class CommandHandler {
    private static final Logger logger = LoggerFactory.getLogger(CommandHandler.class);

    private static final int PLAYBACK_BEFORE_SECONDS = 15;
    private static final int PLAYBACK_AFTER_SECONDS = 15;

    private DeviceManager deviceManager;
    private Recorder recorder;
    private CaptureService captureService;
    private PTZService ptzService;
    private PlaybackService playbackService;
    private RecorderService recorderService;
    private OssService ossService;
    private RecordingTaskService recordingTaskService;
    private ObjectMapper objectMapper = new ObjectMapper();

    public CommandHandler(DeviceManager deviceManager, com.digital.video.gateway.hikvision.HikvisionSDK sdk, Recorder recorder,
                         CaptureService captureService, PTZService ptzService, PlaybackService playbackService) {
        this.deviceManager = deviceManager;
        this.recorder = recorder;
        this.captureService = captureService;
        this.ptzService = ptzService;
        this.playbackService = playbackService;
    }

    public void setRecorderService(RecorderService recorderService) {
        this.recorderService = recorderService;
    }

    public void setOssService(OssService ossService) {
        this.ossService = ossService;
    }

    public void setRecordingTaskService(RecordingTaskService recordingTaskService) {
        this.recordingTaskService = recordingTaskService;
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

    private CommandResponse handleCapture(int userId, String deviceId, String requestId) {
        try {
            DeviceInfo device = deviceManager.getDevice(deviceId);
            if (device == null) {
                return createErrorResponse(requestId, deviceId, "capture", "设备不存在");
            }

            int channel = device.getChannel() > 0 ? device.getChannel() : 1;

            String picFilePath = captureService.captureSnapshot(deviceId, channel);
            if (picFilePath == null) {
                return createErrorResponse(requestId, deviceId, "capture", "抓图失败");
            }

            File picFile = new File(picFilePath);
            if (!picFile.exists()) {
                return createErrorResponse(requestId, deviceId, "capture", "抓图文件未生成");
            }

            byte[] imageBytes = Files.readAllBytes(Paths.get(picFilePath));
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

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
     * 通过 DeviceSDK 统一接口重启设备，支持海康/天地伟业/大华
     */
    private CommandResponse handleReboot(int userId, String deviceId, String requestId) {
        try {
            DeviceSDK deviceSDK = deviceManager.getDeviceSDK(deviceId);
            if (deviceSDK == null) {
                return createErrorResponse(requestId, deviceId, "reboot", "未找到设备对应的SDK实例");
            }

            boolean result = deviceSDK.rebootDevice(userId);
            if (!result) {
                String errorMsg = deviceSDK.getLastErrorString();
                logger.error("重启设备失败: deviceId={}, brand={}, error={}", deviceId, deviceSDK.getBrand(), errorMsg);
                return createErrorResponse(requestId, deviceId, "reboot", "重启设备失败: " + errorMsg);
            }

            Map<String, Object> rebootData = new HashMap<>();
            rebootData.put("message", "设备重启命令已发送");
            rebootData.put("device_id", deviceId);
            rebootData.put("brand", deviceSDK.getBrand());
            logger.info("设备重启命令已发送: deviceId={}, brand={}", deviceId, deviceSDK.getBrand());
            return createSuccessResponse(requestId, deviceId, "reboot", rebootData);

        } catch (Exception e) {
            logger.error("重启失败", e);
            return createErrorResponse(requestId, deviceId, "reboot", "重启异常: " + e.getMessage());
        }
    }

    /**
     * 录像回放：以接收到命令的时间为基准，提取前后各15秒（共30秒）的录像，
     * 上传至OSS后返回OSS地址。
     *
     * 流程：
     * 1. 以当前时间为基准计算 [now-15s, now+15s]
     * 2. 等待 endMs 时间到达 + ZLM 分段写入完成
     * 3. 海康：从ZLM本地循环录像提取合并
     *    其他品牌：通过SDK下载
     * 4. 上传OSS
     * 5. 返回OSS URL
     */
    private CommandResponse handlePlayback(int userId, String deviceId, String requestId, Map<String, Object> command) {
        try {
            DeviceInfo device = deviceManager.getDevice(deviceId);
            if (device == null) {
                return createErrorResponse(requestId, deviceId, "playback", "设备不存在");
            }

            int channel = command.get("channel") != null
                    ? ((Number) command.get("channel")).intValue()
                    : (device.getChannel() > 0 ? device.getChannel() : 1);

            long nowMs = System.currentTimeMillis();
            long startMs = nowMs - PLAYBACK_BEFORE_SECONDS * 1000L;
            long endMs = nowMs + PLAYBACK_AFTER_SECONDS * 1000L;

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String startTimeStr = sdf.format(new Date(startMs));
            String endTimeStr = sdf.format(new Date(endMs));

            logger.info("录像回放命令: deviceId={}, 时间范围=[{} ~ {}]", deviceId, startTimeStr, endTimeStr);

            String brand = device.getBrand() != null ? device.getBrand().toLowerCase() : "";
            String localPath = null;

            if (DeviceInfo.BRAND_HIKVISION.equals(brand) && recorderService != null) {
                // === 海康：从ZLM本地循环录像提取 ===
                long minWaitUntil = endMs + 2000L;
                long now = System.currentTimeMillis();
                if (now < minWaitUntil) {
                    long w = minWaitUntil - now;
                    logger.info("playback: 等待 {}s 确保后段录像写入完成...", w / 1000);
                    Thread.sleep(w);
                }

                int maxAttempts = 20;
                int pollIntervalMs = 3000;
                for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                    localPath = recorderService.extractRecording(deviceId, startMs, endMs);
                    if (localPath != null) {
                        logger.info("playback: ZLM录像提取成功（第{}次尝试）: {}", attempt, localPath);
                        break;
                    }
                    if (attempt < maxAttempts) {
                        logger.debug("playback: ZLM分段尚未就绪，{}s后重试（{}/{}）", pollIntervalMs / 1000, attempt, maxAttempts);
                        Thread.sleep(pollIntervalMs);
                    }
                }
            } else if (recordingTaskService != null) {
                // === 天地伟业/大华/其他：SDK下载 ===
                long minWaitUntil = endMs + 2000L;
                long now = System.currentTimeMillis();
                if (now < minWaitUntil) {
                    Thread.sleep(minWaitUntil - now);
                }

                RecordingTask task = recordingTaskService.downloadRecordingSync(
                        deviceId, channel, startTimeStr, endTimeStr, 120, false);
                if (task != null && task.getStatus() == 2) {
                    localPath = task.getLocalFilePath();
                    logger.info("playback: SDK录像下载成功: {}", localPath);
                } else {
                    int status = task != null ? task.getStatus() : -1;
                    logger.error("playback: SDK录像下载失败: deviceId={}, status={}", deviceId, status);
                }
            }

            if (localPath == null) {
                return createErrorResponse(requestId, deviceId, "playback", "录像提取失败，未找到覆盖时间范围的录像");
            }

            // === 上传OSS ===
            String ossUrl = null;
            if (ossService != null && ossService.isEnabled()) {
                String ossPath = "recordings/" + deviceId + "/" + new File(localPath).getName();
                ossUrl = ossService.uploadFile(localPath, ossPath);
                if (ossUrl != null) {
                    logger.info("playback: 录像已上传OSS: {}", ossUrl);
                } else {
                    logger.warn("playback: OSS上传失败，仅返回本地路径");
                }
            }

            File videoFile = new File(localPath);
            Map<String, Object> playbackData = new HashMap<>();
            playbackData.put("file_path", localPath);
            playbackData.put("file_size", videoFile.length());
            playbackData.put("channel", channel);
            playbackData.put("start_time", startTimeStr);
            playbackData.put("end_time", endTimeStr);
            playbackData.put("duration", PLAYBACK_BEFORE_SECONDS + PLAYBACK_AFTER_SECONDS);
            if (ossUrl != null && !ossUrl.isEmpty()) {
                playbackData.put("oss_url", ossUrl);
            }

            logger.info("playback: 完成, deviceId={}, ossUrl={}, fileSize={}",
                    deviceId, ossUrl, videoFile.length());
            return createSuccessResponse(requestId, deviceId, "playback", playbackData);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return createErrorResponse(requestId, deviceId, "playback", "录像回放被中断");
        } catch (Exception e) {
            logger.error("录像回放失败", e);
            return createErrorResponse(requestId, deviceId, "playback", "录像回放异常: " + e.getMessage());
        }
    }

    /**
     * 播放声音：通过系统音频播放器播放本地音频文件
     */
    private CommandResponse handlePlayAudio(int userId, String deviceId, String requestId, Map<String, Object> command) {
        try {
            String audioPath = (String) command.get("audio_path");
            if (audioPath == null || audioPath.isBlank()) {
                return createErrorResponse(requestId, deviceId, "play_audio", "audio_path 参数不能为空");
            }

            File audioFile = new File(audioPath);
            if (!audioFile.exists()) {
                return createErrorResponse(requestId, deviceId, "play_audio", "音频文件不存在: " + audioPath);
            }

            String player = findSystemPlayer();
            if (player == null) {
                return createErrorResponse(requestId, deviceId, "play_audio", "未找到可用的音频播放器(mpv/ffplay/aplay)");
            }

            ProcessBuilder pb;
            if (player.equals("ffplay")) {
                pb = new ProcessBuilder(player, "-nodisp", "-autoexit", "-loglevel", "quiet", audioPath);
            } else if (player.equals("mpv")) {
                pb = new ProcessBuilder(player, "--no-video", "--really-quiet", audioPath);
            } else {
                pb = new ProcessBuilder(player, audioPath);
            }
            pb.redirectErrorStream(true);
            Process process = pb.start();

            new Thread(() -> {
                try {
                    int exitCode = process.waitFor();
                    logger.info("play_audio: 播放完成, exitCode={}, file={}", exitCode, audioPath);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "PlayAudio-" + deviceId).start();

            Map<String, Object> data = new HashMap<>();
            data.put("message", "音频播放已启动");
            data.put("audio_path", audioPath);
            data.put("player", player);
            return createSuccessResponse(requestId, deviceId, "play_audio", data);

        } catch (Exception e) {
            logger.error("播放声音失败", e);
            return createErrorResponse(requestId, deviceId, "play_audio", "播放声音异常: " + e.getMessage());
        }
    }

    private String findSystemPlayer() {
        for (String cmd : new String[]{"mpv", "ffplay", "aplay", "paplay"}) {
            try {
                Process p = new ProcessBuilder("which", cmd).redirectErrorStream(true).start();
                if (p.waitFor() == 0) return cmd;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private CommandResponse handlePtzControl(int userId, String deviceId, String requestId, Map<String, Object> command) {
        try {
            DeviceInfo device = deviceManager.getDevice(deviceId);
            if (device == null) {
                return createErrorResponse(requestId, deviceId, "ptz_control", "设备不存在");
            }

            String action = (String) command.get("action");
            String commandStr = (String) command.get("command");
            if (commandStr == null) {
                commandStr = action;
            }
            int speed = command.get("speed") != null ? ((Number) command.get("speed")).intValue() : 5;
            int channel = command.get("channel") != null ? ((Number) command.get("channel")).intValue() : device.getChannel();

            if (action == null || commandStr == null) {
                return createErrorResponse(requestId, deviceId, "ptz_control", "动作参数不能为空");
            }

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
