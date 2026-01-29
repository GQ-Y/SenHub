package com.digital.video.gateway.workflow.handlers;

import com.digital.video.gateway.workflow.FlowContext;
import com.digital.video.gateway.workflow.FlowNodeDefinition;
import com.digital.video.gateway.workflow.FlowNodeHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * AI 推理节点：调用外部 AI 接口分析图片/录像 URL，结果写回 context 供后续 condition 或通知使用。
 * config: apiUrl, method, headers, requestBody, outputVariablePrefix, timeoutSeconds
 */
public class AiInferenceHandler implements FlowNodeHandler {
    private static final Logger logger = LoggerFactory.getLogger(AiInferenceHandler.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public boolean execute(FlowNodeDefinition node, FlowContext context) throws Exception {
        Map<String, Object> cfg = node.getConfig();
        if (cfg == null || cfg.get("apiUrl") == null) {
            logger.warn("ai_inference 节点未配置 apiUrl，跳过");
            return false;
        }
        Map<String, Object> extra = new HashMap<>();
        if (context.getVariables() != null) {
            extra.putAll(context.getVariables());
        }
        if (context.getPayload() != null) {
            extra.putAll(context.getPayload());
        }
        String captureOssUrl = context.getVariables() != null && context.getVariables().get("captureOssUrl") instanceof String
                ? (String) context.getVariables().get("captureOssUrl") : null;
        String captureUrl = context.getVariables() != null && context.getVariables().get("captureUrl") instanceof String
                ? (String) context.getVariables().get("captureUrl") : null;
        String recordOssUrl = context.getVariables() != null && context.getVariables().get("recordOssUrl") instanceof String
                ? (String) context.getVariables().get("recordOssUrl") : null;
        String videoUrl = context.getVariables() != null && context.getVariables().get("videoUrl") instanceof String
                ? (String) context.getVariables().get("videoUrl") : null;
        if (captureOssUrl != null) extra.put("captureUrl", captureOssUrl);
        else if (captureUrl != null) extra.put("captureUrl", captureUrl);
        if (recordOssUrl != null) extra.put("recordUrl", recordOssUrl);
        else if (videoUrl != null) extra.put("recordUrl", videoUrl);

        String urlRaw = cfg.get("apiUrl").toString();
        String url = HandlerUtils.renderTemplate(urlRaw, context, extra);
        if (url == null || url.isBlank()) {
            logger.warn("ai_inference apiUrl 渲染后为空，跳过");
            return false;
        }

        String method = cfg.get("method") instanceof String ? ((String) cfg.get("method")).trim().toUpperCase() : "POST";
        if (method.isEmpty()) method = "POST";

        int timeoutSeconds = 30;
        if (cfg.get("timeoutSeconds") instanceof Number) {
            timeoutSeconds = ((Number) cfg.get("timeoutSeconds")).intValue();
        }
        if (timeoutSeconds <= 0) timeoutSeconds = 30;

        String bodyStr = null;
        if (cfg.get("requestBody") != null) {
            String raw = cfg.get("requestBody").toString();
            bodyStr = HandlerUtils.renderTemplate(raw, context, extra);
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds));

        if (cfg.get("headers") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> headers = (Map<String, Object>) cfg.get("headers");
            for (Map.Entry<String, Object> e : headers.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    String value = HandlerUtils.renderTemplate(e.getValue().toString(), context, extra);
                    builder.header(e.getKey().trim(), value != null ? value : e.getValue().toString());
                }
            }
        }
        if (!"GET".equals(method) && bodyStr != null && !bodyStr.isEmpty()) {
            boolean hasContentType = cfg.get("headers") instanceof Map
                    && ((Map<?, ?>) cfg.get("headers")).containsKey("Content-Type");
            if (!hasContentType) {
                builder.header("Content-Type", "application/json");
            }
            builder.method(method, HttpRequest.BodyPublishers.ofString(bodyStr));
        } else {
            if ("GET".equals(method)) builder.GET();
            else builder.method(method, HttpRequest.BodyPublishers.noBody());
        }

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest request = builder.build();
        logger.debug("ai_inference 请求: {} {}", method, url);
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        boolean success = response.statusCode() >= 200 && response.statusCode() < 300;

        String prefix = cfg.get("outputVariablePrefix") != null ? cfg.get("outputVariablePrefix").toString() : "ai_";
        if (prefix == null) prefix = "ai_";

        context.putVariable(prefix + "responseStatus", response.statusCode());
        if (response.body() != null) {
            context.putVariable(prefix + "responseBody", response.body());
            if (success) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> json = mapper.readValue(response.body(), Map.class);
                    flattenIntoContext(context, prefix, json, "");
                } catch (Exception e) {
                    logger.debug("ai_inference 响应非 JSON 或解析失败: {}", e.getMessage());
                }
            }
        }
        if (!success) {
            context.putVariable(prefix + "error", "HTTP " + response.statusCode());
            logger.warn("ai_inference 失败: status={}, body={}", response.statusCode(), response.body());
        }
        return success;
    }

    @SuppressWarnings("unchecked")
    private static void flattenIntoContext(FlowContext context, String prefix, Map<String, Object> map, String path) {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String key = path.isEmpty() ? e.getKey() : path + "." + e.getKey();
            Object val = e.getValue();
            if (val instanceof Map) {
                flattenIntoContext(context, prefix, (Map<String, Object>) val, key);
            } else if (val != null) {
                context.putVariable(prefix + key.replace(".", "_"), val.toString());
            }
        }
    }
}
