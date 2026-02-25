package com.digital.video.gateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ZLM REST API 客户端（addStreamProxy、delStreamProxy、addFFmpegSource、delFFmpegSource 等）
 */
public class ZlmApiClient {
    private static final Logger logger = LoggerFactory.getLogger(ZlmApiClient.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String baseUrl;
    private final String apiSecret;
    private final HttpClient httpClient;

    public ZlmApiClient(String baseUrl, String apiSecret) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.apiSecret = apiSecret != null ? apiSecret : "";
        this.httpClient = HttpClient.newBuilder().build();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * 添加拉流代理（RTSP/RTMP → ZLM）
     * @param autoClose 无人观看时是否自动关流，节省带宽与设备连接数
     * @return 流的 key，用于后续 delStreamProxy；失败返回 null
     */
    public String addStreamProxy(String vhost, String app, String stream, String pullUrl, boolean autoClose) {
        try {
            Map<String, String> params = new java.util.HashMap<>(Map.of(
                    "secret", apiSecret,
                    "vhost", vhost != null ? vhost : "__defaultVhost__",
                    "app", app,
                    "stream", stream,
                    "url", pullUrl
            ));
            if (autoClose) params.put("auto_close", "true");
            String query = buildQuery(params);
            String url = baseUrl + "/index/api/addStreamProxy?" + query;
            JsonNode root = get(url);
            if (root == null) return null;
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                logger.warn("ZLM addStreamProxy 失败: code={}, msg={}", code, root.path("msg").asText(""));
                return null;
            }
            String key = root.path("data").path("key").asText(null);
            logger.debug("ZLM addStreamProxy 成功: key={}", key);
            return key;
        } catch (Exception e) {
            logger.error("ZLM addStreamProxy 异常: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 关闭拉流代理
     */
    public boolean delStreamProxy(String key) {
        if (key == null || key.isEmpty()) return false;
        try {
            String query = buildQuery(Map.of("secret", apiSecret, "key", key));
            JsonNode root = get(baseUrl + "/index/api/delStreamProxy?" + query);
            return root != null && root.path("code").asInt(-1) == 0;
        } catch (Exception e) {
            logger.error("ZLM delStreamProxy 异常: key={}, {}", key, e.getMessage());
            return false;
        }
    }

    /**
     * 添加 FFmpeg 拉流/转码（用于回放转码）
     * @return 任务 key，用于 delFFmpegSource；失败返回 null
     */
    public String addFFmpegSource(String srcUrl, String dstUrl, long timeoutMs, boolean enableHls, boolean enableMp4, String ffmpegCmdKey) {
        try {
            Map<String, String> params = Map.of(
                    "secret", apiSecret,
                    "src_url", srcUrl,
                    "dst_url", dstUrl,
                    "timeout_ms", String.valueOf(timeoutMs),
                    "enable_hls", String.valueOf(enableHls),
                    "enable_mp4", String.valueOf(enableMp4)
            );
            String query = buildQuery(params);
            if (ffmpegCmdKey != null && !ffmpegCmdKey.isEmpty()) {
                query += "&ffmpeg_cmd_key=" + URLEncoder.encode(ffmpegCmdKey, StandardCharsets.UTF_8);
            }
            JsonNode root = get(baseUrl + "/index/api/addFFmpegSource?" + query);
            if (root == null) return null;
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                logger.warn("ZLM addFFmpegSource 失败: code={}, msg={}", code, root.path("msg").asText(""));
                return null;
            }
            return root.path("data").path("key").asText(null);
        } catch (Exception e) {
            logger.error("ZLM addFFmpegSource 异常: {}", e.getMessage());
            return null;
        }
    }

    public boolean delFFmpegSource(String key) {
        if (key == null || key.isEmpty()) return false;
        try {
            String query = buildQuery(Map.of("secret", apiSecret, "key", key));
            JsonNode root = get(baseUrl + "/index/api/delFFmpegSource?" + query);
            return root != null && root.path("code").asInt(-1) == 0;
        } catch (Exception e) {
            logger.error("ZLM delFFmpegSource 异常: key={}, {}", key, e.getMessage());
            return false;
        }
    }

    private String buildQuery(Map<String, String> params) {
        return params.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private JsonNode get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) {
            logger.warn("ZLM API 请求非 200: status={}, url={}, body={}", resp.statusCode(), url.replaceAll("secret=[^&]+", "secret=***"), resp.body());
            return null;
        }
        String body = resp.body();
        if (body == null || body.isEmpty()) {
            logger.warn("ZLM API 响应体为空: url={}", url.replaceAll("secret=[^&]+", "secret=***"));
            return null;
        }
        return JSON.readTree(body);
    }
}
