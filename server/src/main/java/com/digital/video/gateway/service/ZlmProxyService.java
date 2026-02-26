package com.digital.video.gateway.service;

import com.aizuda.zlm4j.callback.IMKProxyPlayerCallBack;
import com.aizuda.zlm4j.core.ZLMApi;
import com.aizuda.zlm4j.structure.MK_INI;
import com.aizuda.zlm4j.structure.MK_PROXY_PLAYER;
import com.digital.video.gateway.database.DeviceInfo;

import com.digital.video.gateway.device.DeviceManager;
import com.sun.jna.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ZLM 拉流代理服务：按需将设备 RTSP 拉入 ZLM，返回 HTTP-FLV/HLS 播放地址；回放转码（MP4 → FFmpeg → FLV）
 * 内嵌 ZLM（zlm4j）使用 C API mk_proxy_player_* 拉流，因 HTTP /index/api/addStreamProxy 在内嵌模式下返回 404。
 */
public class ZlmProxyService {
    private static final Logger logger = LoggerFactory.getLogger(ZlmProxyService.class);
    private static final String VHOST = "__defaultVhost__";
    private static final String APP = "live";

    private final ZLMApi zlmApi;
    private final ZlmApiClient client;
    private final DeviceManager deviceManager;
    private final int httpPort;
    private final int rtmpPort;
    /** 内嵌 ZLM：deviceId -> mk_proxy_player 实例，用于关流 */
    private final Map<String, MK_PROXY_PLAYER> deviceToPlayer = new ConcurrentHashMap<>();
    /** 保持 JNA 回调强引用，防止 GC 导致回调失效 */
    private final Map<String, IMKProxyPlayerCallBack> deviceToPlayCallback = new ConcurrentHashMap<>();
    private final Map<String, IMKProxyPlayerCallBack> deviceToCloseCallback = new ConcurrentHashMap<>();
    /** 独立 ZLM 时：deviceId -> HTTP API 返回的 key */
    private final Map<String, String> deviceToKey = new ConcurrentHashMap<>();
    /** 等待首次播放结果：player Pointer -> 结果持有者（仅创建代理时短暂使用） */
    private final Map<Pointer, PlayResultHolder> pendingPlayResults = new ConcurrentHashMap<>();

    private static final class PlayResultHolder {
        final CountDownLatch latch = new CountDownLatch(1);
        volatile int err = -1;
        volatile String msg = "";
    }

    /**
     * @param zlmApi 内嵌 ZLM 的 C API，非 null 时直播拉流走 mk_proxy_player_*，否则走 HTTP addStreamProxy
     * @param client HTTP API 客户端，用于回放转码及（zlmApi 为 null 时）直播拉流
     */
    public ZlmProxyService(ZLMApi zlmApi, ZlmApiClient client, DeviceManager deviceManager, int zlmHttpPort, int zlmRtmpPort) {
        this.zlmApi = zlmApi;
        this.client = client;
        this.deviceManager = deviceManager;
        this.httpPort = zlmHttpPort;
        this.rtmpPort = zlmRtmpPort;
    }

    /**
     * 获取设备直播地址（按需拉流：首次请求时 addStreamProxy）
     * @param deviceId 设备 ID
     * @param hostForUrl 用于拼播放 URL 的主机（如 request.getHost()），若为空则用 127.0.0.1
     * @return map 含 flv_url、hls_url；若无 RTSP 或拉流失败返回 null
     */
    public Map<String, String> getLiveUrl(String deviceId, String hostForUrl) {
        if (deviceManager == null) return null;
        DeviceInfo device = deviceManager.getDevice(deviceId);
        if (device == null) {
            logger.warn("获取直播地址时设备不存在: deviceId={}", deviceId);
            return null;
        }
        // 始终根据当前设备品牌/IP/端口实时生成 RTSP URL，不信任数据库中可能过时的 rtspUrl
        // （品牌从 auto 改为具体值后，存储的 URL 路径和端口可能已不正确）
        String rtspUrl = buildRtspUrlFromDevice(device);
        if (rtspUrl == null || rtspUrl.isEmpty()) {
            rtspUrl = device.getRtspUrl();
        }
        if (rtspUrl == null || rtspUrl.isEmpty()) {
            logger.warn("设备无 RTSP 地址且无法根据 IP/端口生成: deviceId={}, ip={}, port={}, brand={}", deviceId, device.getIp(), device.getPort(), device.getBrand());
            return null;
        }
        String streamId = sanitizeStreamId(deviceId);

        if (zlmApi != null) {
            synchronized (deviceToPlayer) {
                MK_PROXY_PLAYER player = deviceToPlayer.get(deviceId);
                if (player == null) {
                    logger.info("开始创建拉流代理: deviceId={}, rtsp={}", deviceId, rtspUrl.replaceAll(":[^:@]+@", ":****@"));
                    MK_INI option = zlmApi.mk_ini_create();
                    if (option == null) {
                        logger.warn("ZLM mk_ini_create 失败: deviceId={}", deviceId);
                        return null;
                    }
                    try {
                        zlmApi.mk_ini_set_option_int(option, "enable_mp4", 0);
                        zlmApi.mk_ini_set_option_int(option, "enable_audio", 1);
                        zlmApi.mk_ini_set_option_int(option, "enable_fmp4", 1);
                        zlmApi.mk_ini_set_option_int(option, "enable_ts", 1);
                        zlmApi.mk_ini_set_option_int(option, "enable_hls", 1);
                        zlmApi.mk_ini_set_option_int(option, "enable_rtsp", 1);
                        zlmApi.mk_ini_set_option_int(option, "enable_rtmp", 1);
                        zlmApi.mk_ini_set_option_int(option, "add_mute_audio", 0);
                        zlmApi.mk_ini_set_option_int(option, "auto_close", 0);
                        player = zlmApi.mk_proxy_player_create4(VHOST, APP, streamId, option, 0);
                    } finally {
                        zlmApi.mk_ini_release(option);
                    }
                    if (player == null || player.getPointer() == null) {
                        logger.warn("ZLM mk_proxy_player_create4 失败: deviceId={}", deviceId);
                        return null;
                    }
                    PlayResultHolder holder = new PlayResultHolder();
                    pendingPlayResults.put(player.getPointer(), holder);
                    final String logDeviceId = deviceId;
                    IMKProxyPlayerCallBack onResult = (pUser, err, what, sysErr) -> {
                        PlayResultHolder h = pendingPlayResults.remove(pUser);
                        if (h != null) {
                            h.err = err;
                            h.msg = what != null ? what : "";
                            h.latch.countDown();
                        }
                        if (err != 0) {
                            logger.warn("ZLM 拉流代理首次播放失败: deviceId={}, err={}, what={}, sysErr={}", logDeviceId, err, what, sysErr);
                        } else {
                            logger.info("ZLM 拉流代理首次播放成功: deviceId={}", logDeviceId);
                        }
                    };
                    IMKProxyPlayerCallBack onClose = (pUser, err, what, sysErr) -> {
                        deviceToPlayCallback.remove(logDeviceId);
                        deviceToCloseCallback.remove(logDeviceId);
                        synchronized (deviceToPlayer) {
                            for (Iterator<Map.Entry<String, MK_PROXY_PLAYER>> it = deviceToPlayer.entrySet().iterator(); it.hasNext(); ) {
                                if (it.next().getValue().getPointer().equals(pUser)) {
                                    it.remove();
                                    break;
                                }
                            }
                        }
                        zlmApi.mk_proxy_player_release(new MK_PROXY_PLAYER(pUser));
                        logger.info("ZLM 拉流代理已关闭: deviceId={}, err={}, what={}", logDeviceId, err, what);
                    };
                    // 保持回调强引用防止 GC
                    deviceToPlayCallback.put(deviceId, onResult);
                    deviceToCloseCallback.put(deviceId, onClose);

                    zlmApi.mk_proxy_player_set_on_play_result(player, onResult, player.getPointer(), null);
                    zlmApi.mk_proxy_player_set_option(player, "rtp_type", "0");
                    zlmApi.mk_proxy_player_set_option(player, "protocol_timeout_ms", "10000");
                    zlmApi.mk_proxy_player_play(player, rtspUrl);
                    zlmApi.mk_proxy_player_set_on_close(player, onClose, player.getPointer());
                    boolean ok = false;
                    try {
                        ok = holder.latch.await(15, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        logger.warn("等待拉流结果被中断: deviceId={}", deviceId);
                    }
                    if (!ok) {
                        pendingPlayResults.remove(player.getPointer());
                        deviceToPlayCallback.remove(deviceId);
                        deviceToCloseCallback.remove(deviceId);
                        zlmApi.mk_proxy_player_release(player);
                        logger.warn("ZLM 拉流代理超时(15s)未就绪: deviceId={}, stream={}, rtsp={}", deviceId, streamId, rtspUrl.replaceAll(":[^:@]+@", ":****@"));
                        return null;
                    }
                    if (holder.err != 0) {
                        deviceToPlayCallback.remove(deviceId);
                        deviceToCloseCallback.remove(deviceId);
                        zlmApi.mk_proxy_player_release(player);
                        logger.warn("ZLM 拉流代理播放失败: deviceId={}, err={}, msg={}, rtsp={}", deviceId, holder.err, holder.msg, rtspUrl.replaceAll(":[^:@]+@", ":****@"));
                        return null;
                    }
                    deviceToPlayer.put(deviceId, player);
                    logger.info("ZLM 拉流已就绪(mk_proxy_player): deviceId={}, stream={}", deviceId, streamId);
                }
            }
        } else {
            if (client == null) return null;
            String key = deviceToKey.get(deviceId);
            if (key == null) {
                key = client.addStreamProxy(VHOST, APP, streamId, rtspUrl, true);
                if (key == null) {
                    logger.warn("ZLM 添加拉流代理失败: deviceId={}, rtsp={}", deviceId, rtspUrl.replaceAll(":[^:@]+@", ":****@"));
                    return null;
                }
                deviceToKey.put(deviceId, key);
            }
        }

        // 用 IP 访问时 ZLM 需带 vhost 参数才能找到流，见 https://docs.zlmediakit.com/guide/media_server/play_url_rules.html
        String host = (hostForUrl != null && !hostForUrl.isEmpty()) ? hostForUrl : "127.0.0.1";
        String vhostParam = "?vhost=" + VHOST;
        String base = "http://" + host + ":" + httpPort + "/" + APP + "/" + streamId;
        Map<String, String> out = new HashMap<>();
        out.put("flv_url", base + ".live.flv" + vhostParam);
        out.put("hls_url", base + "/hls.m3u8" + vhostParam);
        return out;
    }

    /**
     * 关闭设备拉流（无人观看时调用）
     */
    public void closeStream(String deviceId) {
        if (zlmApi != null) {
            MK_PROXY_PLAYER player = deviceToPlayer.remove(deviceId);
            deviceToPlayCallback.remove(deviceId);
            deviceToCloseCallback.remove(deviceId);
            if (player != null) {
                zlmApi.mk_proxy_player_release(player);
                logger.debug("已关闭设备拉流(mk_proxy_player): deviceId={}", deviceId);
            }
        } else if (client != null) {
            String key = deviceToKey.remove(deviceId);
            if (key != null) {
                client.delStreamProxy(key);
                logger.debug("已关闭设备拉流: deviceId={}", deviceId);
            }
        }
    }

    /** FFmpeg 推流进程：key -> Process */
    private final Map<String, Process> ffmpegProcesses = new ConcurrentHashMap<>();
    private final AtomicLong ffmpegKeySeq = new AtomicLong(0);

    /**
     * 回放转码：启动 FFmpeg 子进程将本地 MP4 推流到 ZLM RTMP，前端通过 HTTP-FLV 播放。
     * 内嵌 ZLM 模式下 HTTP API addFFmpegSource 不可用，因此直接用 FFmpeg 子进程替代。
     * @param deviceId 设备 ID
     * @param filePath 相对或绝对路径，必须在 ./storage/downloads 下
     * @param hostForUrl 返回地址使用的主机
     * @return map 含 flv_url、key；失败返回 null
     */
    public Map<String, String> getPlaybackTranscodeUrl(String deviceId, String filePath, String hostForUrl) {
        File file = new File(filePath);
        try {
            File downloadsRoot = new File("./storage/downloads");
            if (!file.getCanonicalPath().startsWith(downloadsRoot.getCanonicalPath())) {
                logger.warn("回放转码拒绝：文件不在 downloads 目录: {}", filePath);
                return null;
            }
            if (!file.exists() || !file.isFile() || file.length() == 0) {
                logger.warn("回放转码拒绝：文件不存在或为空: {}", filePath);
                return null;
            }
        } catch (Exception e) {
            logger.warn("回放转码路径校验失败: {}", e.getMessage());
            return null;
        }
        String streamId = "playback_" + sanitizeStreamId(deviceId) + "_" + System.currentTimeMillis();
        String dstUrl = "rtmp://127.0.0.1:" + rtmpPort + "/" + APP + "/" + streamId;
        String key = "ffmpeg_" + ffmpegKeySeq.incrementAndGet();

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg",
                    "-re",
                    "-i", file.getAbsolutePath(),
                    "-c", "copy",
                    "-f", "flv",
                    dstUrl
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 消费 stdout/stderr 避免进程阻塞
            Thread convergThread = new Thread(() -> {
                try (var is = process.getInputStream()) {
                    byte[] buf = new byte[4096];
                    while (is.read(buf) != -1) { /* drain */ }
                } catch (Exception ignored) {}
            }, "ffmpeg-drain-" + key);
            convergThread.setDaemon(true);
            convergThread.start();

            // 等待 FFmpeg 短暂启动，确认进程存活
            Thread.sleep(1500);
            if (!process.isAlive()) {
                int exitCode = process.exitValue();
                logger.warn("FFmpeg 推流进程启动后立即退出: exitCode={}, file={}", exitCode, filePath);
                return null;
            }

            ffmpegProcesses.put(key, process);
            logger.info("FFmpeg 推流已启动: key={}, file={}, dst={}", key, file.getName(), dstUrl);
        } catch (Exception e) {
            logger.error("启动 FFmpeg 推流失败: {}", e.getMessage(), e);
            return null;
        }

        String host = (hostForUrl != null && !hostForUrl.isEmpty()) ? hostForUrl : "127.0.0.1";
        String flvUrl = "http://" + host + ":" + httpPort + "/" + APP + "/" + streamId + ".live.flv?vhost=" + VHOST;
        Map<String, String> out = new HashMap<>();
        out.put("flv_url", flvUrl);
        out.put("key", key);
        return out;
    }

    /**
     * 停止回放转码任务（销毁 FFmpeg 子进程）
     */
    public boolean stopPlaybackTranscode(String key) {
        Process process = ffmpegProcesses.remove(key);
        if (process != null) {
            process.destroyForcibly();
            logger.info("FFmpeg 推流已停止: key={}", key);
            return true;
        }
        if (client != null) {
            return client.delFFmpegSource(key);
        }
        return false;
    }

    private static final int DEFAULT_RTSP_PORT = 554;

    /**
     * 根据设备品牌/IP/账号生成 RTSP 地址，RTSP 端口统一使用各品牌默认值 554，与数据库中的管理端口无关。
     */
    private static String buildRtspUrlFromDevice(DeviceInfo device) {
        String ip = device.getIp();
        if (ip == null || ip.isEmpty()) return null;
        String username = device.getUsername();
        String password = device.getPassword();
        String brand = device.getBrand() != null ? device.getBrand().toLowerCase() : "";
        String path = getRtspPathByBrand(brand, device.getChannel());
        if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
            try {
                String encUser = URLEncoder.encode(username, StandardCharsets.UTF_8);
                String encPass = URLEncoder.encode(password, StandardCharsets.UTF_8);
                return "rtsp://" + encUser + ":" + encPass + "@" + ip + ":" + DEFAULT_RTSP_PORT + path;
            } catch (Exception e) {
                return "rtsp://" + ip + ":" + DEFAULT_RTSP_PORT + path;
            }
        }
        return "rtsp://" + ip + ":" + DEFAULT_RTSP_PORT + path;
    }

    /**
     * 根据品牌返回 RTSP 路径。
     * 天地伟业：/video1；大华：/cam/realmonitor?channel=N&subtype=0；海康/默认：/Streaming/Channels/101
     */
    private static String getRtspPathByBrand(String brand, int channel) {
        if (brand == null) brand = "";
        switch (brand.toLowerCase()) {
            case "tiandy":
                return "/video1";
            case "dahua":
                int ch = channel > 0 ? channel : 1;
                return "/cam/realmonitor?channel=" + ch + "&subtype=0";
            default:
                return "/Streaming/Channels/101";
        }
    }

    private static String sanitizeStreamId(String deviceId) {
        if (deviceId == null) return "unknown";
        return deviceId.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
