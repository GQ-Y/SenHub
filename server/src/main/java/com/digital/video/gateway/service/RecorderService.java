package com.digital.video.gateway.service;

import com.aizuda.zlm4j.callback.IMKProxyPlayerCallBack;
import com.aizuda.zlm4j.core.ZLMApi;
import com.aizuda.zlm4j.structure.MK_INI;
import com.aizuda.zlm4j.structure.MK_PROXY_PLAYER;
import com.digital.video.gateway.config.Config;
import com.digital.video.gateway.database.DeviceInfo;
import com.digital.video.gateway.device.DeviceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/**
 * 录制服务 — 通过 zlm4j 的 mk_proxy_player (enable_mp4=1) 实现海康摄像头循环录像。
 *
 * ZLM 自动按 record.fileSecond（180s = 3 分钟）切片录像为 MP4 文件，
 * 存储目录结构：{record.filePath}/{vhost}/{app}/{streamId}/{YYYY-MM-DD}/{HH-MM-SS}.mp4
 *
 * 天地伟业/大华等品牌的录像下载通过 SDK 正常完成，不需要本地循环录像。
 */
public class RecorderService {
    private static final Logger logger = LoggerFactory.getLogger(RecorderService.class);

    private static final String VHOST = "__defaultVhost__";
    private static final String APP = "live";
    private static final int SEGMENT_SECONDS = ZlmMediaService.RECORD_FILE_SECOND;
    private static final int DEFAULT_RTSP_PORT = 554;

    private final DeviceManager deviceManager;
    private final Config.RecorderConfig config;
    private final ZlmMediaService zlmMediaService;

    private final Map<String, MK_PROXY_PLAYER> deviceToPlayer = new ConcurrentHashMap<>();
    private final Map<String, IMKProxyPlayerCallBack> deviceToPlayCallback = new ConcurrentHashMap<>();
    private final Map<String, IMKProxyPlayerCallBack> deviceToCloseCallback = new ConcurrentHashMap<>();
    private final Set<String> activeDevices = ConcurrentHashMap.newKeySet();

    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "RecorderWatchdog");
        t.setDaemon(true);
        return t;
    });

    public RecorderService(DeviceManager deviceManager, Config.RecorderConfig config, ZlmMediaService zlmMediaService) {
        this.deviceManager = deviceManager;
        this.config = config;
        this.zlmMediaService = zlmMediaService;
        watchdog.scheduleWithFixedDelay(this::checkStreams, 60, 30, TimeUnit.SECONDS);
    }

    // ======================== 公共 API ========================

    /**
     * 启动设备录制。仅海康设备需要本地 ZLM 循环录像。
     */
    public boolean startRecording(String deviceId) {
        if (!config.isEnabled()) {
            logger.debug("录制功能已禁用");
            return false;
        }

        DeviceInfo device = deviceManager.getDevice(deviceId);
        if (device == null) {
            logger.warn("设备不存在，无法启动录制: {}", deviceId);
            return false;
        }

        String brand = device.getBrand() != null ? device.getBrand().toLowerCase() : "";
        if (!DeviceInfo.BRAND_HIKVISION.equals(brand)) {
            logger.debug("设备 {} 品牌为 {}，不需要本地循环录像（SDK下载正常）", deviceId, brand);
            return true;
        }

        if (!zlmMediaService.isStarted()) {
            logger.warn("ZLM 未启动，无法为海康设备创建录像代理: {}", deviceId);
            return false;
        }

        if (deviceToPlayer.containsKey(deviceId)) {
            logger.debug("设备 {} 已在录制中", deviceId);
            return true;
        }

        ZLMApi api = zlmMediaService.getApi();
        if (api == null) {
            logger.warn("ZLMApi 不可用");
            return false;
        }

        String rtspUrl = buildRtspUrl(device);
        if (rtspUrl == null || rtspUrl.isEmpty()) {
            logger.warn("无法构建 RTSP 地址: deviceId={}", deviceId);
            return false;
        }

        String streamId = "rec_" + sanitizeStreamId(deviceId);

        MK_INI option = api.mk_ini_create();
        if (option == null) {
            logger.warn("mk_ini_create 失败: deviceId={}", deviceId);
            return false;
        }

        try {
            api.mk_ini_set_option_int(option, "enable_mp4", 1);
            api.mk_ini_set_option_int(option, "enable_audio", 0);
            api.mk_ini_set_option_int(option, "enable_hls", 0);
            api.mk_ini_set_option_int(option, "enable_fmp4", 0);
            api.mk_ini_set_option_int(option, "enable_ts", 0);
            api.mk_ini_set_option_int(option, "enable_rtsp", 0);
            api.mk_ini_set_option_int(option, "enable_rtmp", 0);
            api.mk_ini_set_option_int(option, "auto_close", 0);
            api.mk_ini_set_option_int(option, "add_mute_audio", 0);
            api.mk_ini_set_option(option, "mp4_save_path", ZlmMediaService.getRecordBasePathAbsolute());
            api.mk_ini_set_option_int(option, "mp4_max_second", SEGMENT_SECONDS);

            MK_PROXY_PLAYER player = api.mk_proxy_player_create4(VHOST, APP, streamId, option, 0);
            if (player == null || player.getPointer() == null) {
                logger.warn("mk_proxy_player_create4 失败: deviceId={}", deviceId);
                return false;
            }

            final String logDeviceId = deviceId;
            final CountDownLatch latch = new CountDownLatch(1);
            final int[] resultErr = {-1};

            IMKProxyPlayerCallBack onResult = (pUser, err, what, sysErr) -> {
                resultErr[0] = err;
                latch.countDown();
                if (err != 0) {
                    logger.warn("ZLM 录像拉流失败: deviceId={}, err={}, what={}", logDeviceId, err, what);
                } else {
                    logger.info("ZLM 录像拉流成功: deviceId={}, stream={}", logDeviceId, "rec_" + sanitizeStreamId(logDeviceId));
                }
            };

            IMKProxyPlayerCallBack onClose = (pUser, err, what, sysErr) -> {
                deviceToPlayer.remove(logDeviceId);
                deviceToPlayCallback.remove(logDeviceId);
                deviceToCloseCallback.remove(logDeviceId);
                api.mk_proxy_player_release(new MK_PROXY_PLAYER(pUser));
                logger.warn("ZLM 录像代理已关闭: deviceId={}, err={}, what={}", logDeviceId, err, what);
            };

            deviceToPlayCallback.put(deviceId, onResult);
            deviceToCloseCallback.put(deviceId, onClose);

            api.mk_proxy_player_set_on_play_result(player, onResult, player.getPointer(), null);
            api.mk_proxy_player_set_option(player, "rtp_type", "0");
            api.mk_proxy_player_set_option(player, "protocol_timeout_ms", "10000");
            api.mk_proxy_player_play(player, rtspUrl);
            api.mk_proxy_player_set_on_close(player, onClose, player.getPointer());

            boolean ok = false;
            try {
                ok = latch.await(15, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            if (!ok || resultErr[0] != 0) {
                deviceToPlayCallback.remove(deviceId);
                deviceToCloseCallback.remove(deviceId);
                api.mk_proxy_player_release(player);
                logger.warn("ZLM 录像代理启动失败或超时: deviceId={}, rtsp={}", deviceId, rtspUrl.replaceAll(":[^:@]+@", ":****@"));
                return false;
            }

            deviceToPlayer.put(deviceId, player);
            activeDevices.add(deviceId);
            logger.info("海康设备 {} ZLM 循环录像已启动 (stream={}, 分段={}s)", deviceId, streamId, SEGMENT_SECONDS);
            return true;

        } finally {
            api.mk_ini_release(option);
        }
    }

    public boolean stopRecording(String deviceId) {
        activeDevices.remove(deviceId);
        MK_PROXY_PLAYER player = deviceToPlayer.remove(deviceId);
        deviceToPlayCallback.remove(deviceId);
        deviceToCloseCallback.remove(deviceId);
        if (player != null) {
            ZLMApi api = zlmMediaService.getApi();
            if (api != null) {
                api.mk_proxy_player_release(player);
            }
            logger.info("设备 {} 录制已停止", deviceId);
            return true;
        }
        return false;
    }

    public String getCurrentRecordingFile(String deviceId) {
        if (!deviceToPlayer.containsKey(deviceId)) return null;
        return findLatestSegment(deviceId);
    }

    /**
     * 从 ZLM 录像的 MP4 分段中提取指定时间范围的录像。
     *
     * @param deviceId 设备 ID
     * @param startMs  起始时间（epoch ms）
     * @param endMs    结束时间（epoch ms）
     * @return 合并后的 MP4 文件路径；失败返回 null
     */
    public String extractRecording(String deviceId, long startMs, long endMs) {
        List<SegmentFile> segments = findOverlappingSegments(deviceId, startMs, endMs);
        if (segments.isEmpty()) {
            logger.warn("未找到覆盖时间范围的 ZLM 录像分段: deviceId={}, range=[{} ~ {}]",
                    deviceId, fmtMs(startMs), fmtMs(endMs));
            return null;
        }

        // 检查已完成的分段是否完全覆盖请求的时间范围
        long latestEndMs = 0;
        for (SegmentFile seg : segments) {
            long segEnd = seg.startMs + SEGMENT_SECONDS * 1000L;
            if (segEnd > latestEndMs) latestEndMs = segEnd;
        }
        if (latestEndMs < endMs) {
            logger.debug("已完成分段仅覆盖到 {}，尚未覆盖到 endMs={}，等待更多分段", fmtMs(latestEndMs), fmtMs(endMs));
            return null;
        }

        logger.info("找到 {} 个 ZLM 分段完全覆盖时间范围 [{} ~ {}]", segments.size(), fmtMs(startMs), fmtMs(endMs));

        File outDir = new File("./storage/recordings");
        if (!outDir.exists()) outDir.mkdirs();
        String safeId = sanitizeStreamId(deviceId);
        String suffix = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date(startMs));
        String outputPath = new File(outDir, "alarm_" + safeId + "_" + suffix + ".mp4").getAbsolutePath();

        if (segments.size() == 1) {
            SegmentFile seg = segments.get(0);
            double ssSeconds = Math.max((startMs - seg.startMs) / 1000.0, 0);
            double durationSeconds = (endMs - startMs) / 1000.0;
            return ffmpegExtract(seg.file.getAbsolutePath(), outputPath, ssSeconds, durationSeconds);
        }

        List<String> tmpFiles = new ArrayList<>();
        try {
            for (int i = 0; i < segments.size(); i++) {
                SegmentFile seg = segments.get(i);
                double ss = (i == 0) ? Math.max((startMs - seg.startMs) / 1000.0, 0) : 0;
                double segEnd = (i == segments.size() - 1)
                        ? Math.min((endMs - seg.startMs) / 1000.0, SEGMENT_SECONDS)
                        : SEGMENT_SECONDS;
                double dur = segEnd - ss;
                if (dur <= 0) continue;
                String tmp = new File(outDir, "tmp_seg_" + i + "_" + suffix + ".mp4").getAbsolutePath();
                String result = ffmpegExtract(seg.file.getAbsolutePath(), tmp, ss, dur);
                if (result != null) tmpFiles.add(result);
            }
            if (tmpFiles.isEmpty()) return null;
            if (tmpFiles.size() == 1) {
                new File(tmpFiles.get(0)).renameTo(new File(outputPath));
                return outputPath;
            }
            return ffmpegConcat(tmpFiles, outputPath);
        } finally {
            for (String tmp : tmpFiles) {
                try { Files.deleteIfExists(new File(tmp).toPath()); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * 判断设备是否需要本地 ZLM 循环录像（仅海康）。
     */
    public boolean needsLocalRecording(String deviceId) {
        DeviceInfo device = deviceManager.getDevice(deviceId);
        if (device == null) return false;
        String brand = device.getBrand() != null ? device.getBrand().toLowerCase() : "";
        return DeviceInfo.BRAND_HIKVISION.equals(brand);
    }

    // ======================== 内部方法 ========================

    /**
     * ZLM MP4 录像目录。
     * protocol.mp4_save_path 模式下，ZLM 使用 record.appName（默认 "record"）替代 vhost，
     * 目录结构为：{mp4_save_path}/record/{app}/{streamId}/{YYYY-MM-DD}/
     * 文件命名为：{YYYY-MM-DD-HH-mm-ss-index}.mp4
     */
    private File getRecordingDir(String deviceId) {
        String streamId = "rec_" + sanitizeStreamId(deviceId);
        String base = ZlmMediaService.getRecordBasePathAbsolute();
        return new File(base, "record/" + APP + "/" + streamId);
    }

    private List<SegmentFile> findOverlappingSegments(String deviceId, long startMs, long endMs) {
        File deviceDir = getRecordingDir(deviceId);
        if (!deviceDir.exists()) {
            logger.warn("ZLM 录像目录不存在（请确认该设备 ZLM 循环录像已启动且已录满至少一段 180s）: {}", deviceDir.getAbsolutePath());
            return Collections.emptyList();
        }

        List<SegmentFile> result = new ArrayList<>();
        File[] dateDirs = deviceDir.listFiles(File::isDirectory);
        if (dateDirs == null) return Collections.emptyList();

        // ZLM protocol.mp4_save_path 模式文件名: YYYY-MM-DD-HH-mm-ss-index.mp4
        // 正在写入的文件以 . 开头（隐藏文件），moov 未写入，FFmpeg 无法读取，必须跳过
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        for (File dateDir : dateDirs) {
            File[] mp4Files = dateDir.listFiles((d, name) -> name.endsWith(".mp4") && !name.startsWith("."));
            if (mp4Files == null) continue;

            for (File f : mp4Files) {
                String name = f.getName().replace(".mp4", "");
                // 去掉末尾的 "-index" 部分（如 "2026-02-27-19-48-04-0" → "2026-02-27-19-48-04"）
                int lastDash = name.lastIndexOf('-');
                if (lastDash > 0) {
                    String maybeIndex = name.substring(lastDash + 1);
                    if (maybeIndex.chars().allMatch(Character::isDigit)) {
                        name = name.substring(0, lastDash);
                    }
                }
                try {
                    long fileStartMs = sdf.parse(name).getTime();
                    long fileEndMs = fileStartMs + SEGMENT_SECONDS * 1000L;
                    if (fileStartMs < endMs && fileEndMs > startMs) {
                        result.add(new SegmentFile(f, fileStartMs));
                    }
                } catch (ParseException e) {
                    logger.debug("无法解析 ZLM 录像时间戳: {}", f.getName());
                }
            }
        }

        result.sort(Comparator.comparingLong(a -> a.startMs));
        return result;
    }

    private String findLatestSegment(String deviceId) {
        File deviceDir = getRecordingDir(deviceId);
        if (!deviceDir.exists()) return null;

        File[] dateDirs = deviceDir.listFiles(File::isDirectory);
        if (dateDirs == null || dateDirs.length == 0) return null;

        Arrays.sort(dateDirs, Comparator.comparing(File::getName).reversed());
        for (File dateDir : dateDirs) {
            File[] mp4Files = dateDir.listFiles((d, name) -> name.endsWith(".mp4"));
            if (mp4Files == null || mp4Files.length == 0) continue;
            Arrays.sort(mp4Files, Comparator.comparing(File::getName).reversed());
            return mp4Files[0].getAbsolutePath();
        }
        return null;
    }

    private void checkStreams() {
        for (String deviceId : activeDevices) {
            if (!deviceToPlayer.containsKey(deviceId)) {
                logger.warn("ZLM 录像代理已断开，尝试重新连接: {}", deviceId);
                startRecording(deviceId);
            }
        }
    }

    /**
     * 清理超过保留期的 ZLM 录像文件。
     */
    public void cleanupOldRecords(long retentionMillis) {
        File baseDir = new File(ZlmMediaService.getRecordBasePathAbsolute());
        if (!baseDir.exists()) return;

        long cutoff = System.currentTimeMillis() - retentionMillis;
        int deleted = 0;

        File[] vhostDirs = baseDir.listFiles(File::isDirectory);
        if (vhostDirs == null) return;
        for (File vhost : vhostDirs) {
            File[] appDirs = vhost.listFiles(File::isDirectory);
            if (appDirs == null) continue;
            for (File app : appDirs) {
                File[] streamDirs = app.listFiles(File::isDirectory);
                if (streamDirs == null) continue;
                for (File stream : streamDirs) {
                    File[] dateDirs = stream.listFiles(File::isDirectory);
                    if (dateDirs == null) continue;
                    for (File dateDir : dateDirs) {
                        File[] files = dateDir.listFiles((d, name) -> name.endsWith(".mp4"));
                        if (files == null) continue;
                        for (File f : files) {
                            if (f.lastModified() < cutoff) {
                                if (f.delete()) deleted++;
                            }
                        }
                        String[] remaining = dateDir.list();
                        if (remaining != null && remaining.length == 0) dateDir.delete();
                    }
                }
            }
        }

        if (deleted > 0) {
            logger.info("清理了 {} 个过期 ZLM 录像文件", deleted);
        }
    }

    // ======================== ffmpeg 辅助 ========================

    private String ffmpegExtract(String input, String output, double ss, double duration) {
        try {
            List<String> cmd = new ArrayList<>(Arrays.asList(
                    "ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
                    "-ss", String.format(Locale.US, "%.3f", ss),
                    "-i", input,
                    "-t", String.format(Locale.US, "%.3f", duration),
                    "-c", "copy",
                    "-movflags", "+faststart",
                    output));
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String err = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean ok = p.waitFor(120, TimeUnit.SECONDS);
            if (!ok) { p.destroyForcibly(); logger.error("ffmpeg 裁剪超时"); return null; }
            if (p.exitValue() != 0) { logger.error("ffmpeg 裁剪失败: {}", err); return null; }
            if (new File(output).exists() && new File(output).length() > 0) return output;
            return null;
        } catch (Exception e) {
            logger.error("ffmpeg 裁剪异常", e);
            return null;
        }
    }

    private String ffmpegConcat(List<String> inputs, String output) {
        try {
            File listFile = new File(output + ".list.txt");
            StringBuilder sb = new StringBuilder();
            for (String f : inputs) sb.append("file '").append(f).append("'\n");
            Files.writeString(listFile.toPath(), sb.toString());
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
                    "-f", "concat", "-safe", "0", "-i", listFile.getAbsolutePath(),
                    "-c", "copy", "-movflags", "+faststart", output);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String err = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean ok = p.waitFor(120, TimeUnit.SECONDS);
            Files.deleteIfExists(listFile.toPath());
            if (!ok) { p.destroyForcibly(); return null; }
            if (p.exitValue() != 0) { logger.error("ffmpeg concat 失败: {}", err); return null; }
            return new File(output).exists() ? output : null;
        } catch (Exception e) {
            logger.error("ffmpeg concat 异常", e);
            return null;
        }
    }

    // ======================== RTSP URL ========================

    static String buildRtspUrl(DeviceInfo device) {
        String ip = device.getIp();
        if (ip == null || ip.isEmpty()) return null;
        String user = device.getUsername();
        String pass = device.getPassword();
        String brand = device.getBrand() != null ? device.getBrand().toLowerCase() : "";
        String path = getRtspPathByBrand(brand, device.getChannel());
        if (user != null && !user.isEmpty() && pass != null && !pass.isEmpty()) {
            try {
                return "rtsp://" + URLEncoder.encode(user, StandardCharsets.UTF_8) + ":"
                        + URLEncoder.encode(pass, StandardCharsets.UTF_8) + "@" + ip + ":" + DEFAULT_RTSP_PORT + path;
            } catch (Exception ignored) {}
        }
        return "rtsp://" + ip + ":" + DEFAULT_RTSP_PORT + path;
    }

    static String getRtspPathByBrand(String brand, int channel) {
        if (brand == null) brand = "";
        switch (brand) {
            case "tiandy":  return "/video1";
            case "dahua":   return "/cam/realmonitor?channel=" + Math.max(channel, 1) + "&subtype=0";
            default:        return "/Streaming/Channels/" + Math.max(channel, 1) + "01";
        }
    }

    static String sanitizeStreamId(String deviceId) {
        if (deviceId == null) return "unknown";
        return deviceId.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private static String fmtMs(long ms) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(ms));
    }

    // ======================== 内部类 ========================

    private static class SegmentFile {
        final File file;
        final long startMs;
        SegmentFile(File file, long startMs) { this.file = file; this.startMs = startMs; }
    }
}
