package com.digital.video.gateway.service;

import com.digital.video.gateway.database.DeviceInfo;
import com.digital.video.gateway.device.DeviceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ZLM 拉流代理服务：按需将设备 RTSP 拉入 ZLM，返回 HTTP-FLV/HLS 播放地址；回放转码（MP4 → FFmpeg → FLV）
 */
public class ZlmProxyService {
    private static final Logger logger = LoggerFactory.getLogger(ZlmProxyService.class);
    private static final String VHOST = "__defaultVhost__";
    private static final String APP = "live";

    private final ZlmApiClient client;
    private final DeviceManager deviceManager;
    private final int httpPort;
    private final int rtmpPort;
    /** deviceId -> ZLM proxy key，用于后续无人观看关流 */
    private final Map<String, String> deviceToKey = new ConcurrentHashMap<>();

    public ZlmProxyService(ZlmApiClient client, DeviceManager deviceManager, int zlmHttpPort, int zlmRtmpPort) {
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
        if (client == null || deviceManager == null) return null;
        DeviceInfo device = deviceManager.getDevice(deviceId);
        if (device == null) {
            logger.warn("获取直播地址时设备不存在: deviceId={}", deviceId);
            return null;
        }
        String rtspUrl = device.getRtspUrl();
        if (rtspUrl == null || rtspUrl.isEmpty()) {
            rtspUrl = buildRtspUrlFromDevice(device);
            if (rtspUrl != null && !rtspUrl.isEmpty()) {
                logger.info("设备无存储的 RTSP，已根据 IP/端口生成: deviceId={}, rtsp={}", deviceId, rtspUrl.replaceAll(":[^:@]+@", ":****@"));
            }
        }
        if (rtspUrl == null || rtspUrl.isEmpty()) {
            logger.warn("设备无 RTSP 地址且无法根据 IP/端口生成: deviceId={}, ip={}, port={}", deviceId, device.getIp(), device.getPort());
            return null;
        }
        String streamId = sanitizeStreamId(deviceId);
        String key = deviceToKey.get(deviceId);
        if (key == null) {
            key = client.addStreamProxy(VHOST, APP, streamId, rtspUrl, true);
            if (key == null) {
                logger.warn("ZLM 添加拉流代理失败: deviceId={}, rtsp={}", deviceId, rtspUrl.replaceAll(":[^:@]+@", ":****@"));
                return null;
            }
            deviceToKey.put(deviceId, key);
        }
        String host = (hostForUrl != null && !hostForUrl.isEmpty()) ? hostForUrl : "127.0.0.1";
        String base = "http://" + host + ":" + httpPort + "/" + APP + "/" + streamId;
        Map<String, String> out = new HashMap<>();
        out.put("flv_url", base + ".live.flv");
        out.put("hls_url", base + "/hls.m3u8");
        return out;
    }

    /**
     * 关闭设备拉流（无人观看时调用）
     */
    public void closeStream(String deviceId) {
        String key = deviceToKey.remove(deviceId);
        if (key != null) {
            client.delStreamProxy(key);
            logger.debug("已关闭设备拉流: deviceId={}", deviceId);
        }
    }

    /**
     * 回放转码：将本地 MP4 通过 ZLM addFFmpegSource 转为 HTTP-FLV 流（需服务器安装 FFmpeg）
     * @param deviceId 设备 ID
     * @param filePath 相对或绝对路径，必须在 ./storage/downloads 下
     * @param hostForUrl 返回地址使用的主机
     * @return map 含 flv_url、key；失败返回 null
     */
    public Map<String, String> getPlaybackTranscodeUrl(String deviceId, String filePath, String hostForUrl) {
        if (client == null) return null;
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
        String srcUrl = "file://" + file.getAbsolutePath().replace("\\", "/");
        String dstUrl = "rtmp://127.0.0.1:" + rtmpPort + "/" + APP + "/" + streamId;
        String key = client.addFFmpegSource(srcUrl, dstUrl, 60_000L, false, false, null);
        if (key == null) return null;
        String host = (hostForUrl != null && !hostForUrl.isEmpty()) ? hostForUrl : "127.0.0.1";
        String flvUrl = "http://" + host + ":" + httpPort + "/" + APP + "/" + streamId + ".live.flv";
        Map<String, String> out = new HashMap<>();
        out.put("flv_url", flvUrl);
        out.put("key", key);
        return out;
    }

    /**
     * 停止回放转码任务
     */
    public boolean stopPlaybackTranscode(String key) {
        return client != null && client.delFFmpegSource(key);
    }

    /**
     * 根据设备 IP/端口/账号生成 RTSP 地址（与列表接口逻辑一致，海康/天地伟业等登录成功后均有 RTSP）
     */
    private static String buildRtspUrlFromDevice(DeviceInfo device) {
        String ip = device.getIp();
        if (ip == null || ip.isEmpty()) return null;
        int port = device.getPort();
        int rtspPort = (port == 8000) ? 554 : port;
        String username = device.getUsername();
        String password = device.getPassword();
        String path = "/Streaming/Channels/101";
        if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
            try {
                String encUser = URLEncoder.encode(username, StandardCharsets.UTF_8);
                String encPass = URLEncoder.encode(password, StandardCharsets.UTF_8);
                return "rtsp://" + encUser + ":" + encPass + "@" + ip + ":" + rtspPort + path;
            } catch (Exception e) {
                return "rtsp://" + ip + ":" + rtspPort + path;
            }
        }
        return "rtsp://" + ip + ":" + rtspPort + path;
    }

    private static String sanitizeStreamId(String deviceId) {
        if (deviceId == null) return "unknown";
        return deviceId.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
