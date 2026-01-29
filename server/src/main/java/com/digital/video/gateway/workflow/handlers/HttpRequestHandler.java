package com.digital.video.gateway.workflow.handlers;

import com.digital.video.gateway.workflow.FlowContext;
import com.digital.video.gateway.workflow.FlowNodeDefinition;
import com.digital.video.gateway.workflow.FlowNodeHandler;
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
 * 通用 HTTP 请求节点：URL/方法/headers/body 可配，支持占位符渲染。
 */
public class HttpRequestHandler implements FlowNodeHandler {
    private static final Logger logger = LoggerFactory.getLogger(HttpRequestHandler.class);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Override
    public boolean execute(FlowNodeDefinition node, FlowContext context) throws Exception {
        Map<String, Object> cfg = node.getConfig();
        if (cfg == null || cfg.get("url") == null) {
            logger.warn("http_request 节点未配置 url，跳过");
            return false;
        }
        String urlRaw = cfg.get("url").toString();
        Map<String, Object> extra = context.getVariables() != null ? new HashMap<>(context.getVariables()) : new HashMap<>();
        if (context.getPayload() != null) {
            extra.putAll(context.getPayload());
        }
        String url = HandlerUtils.renderTemplate(urlRaw, context, extra);
        if (url == null || url.isBlank()) {
            logger.warn("http_request url 渲染后为空，跳过");
            return false;
        }

        String method = "POST";
        if (cfg.get("method") instanceof String) {
            method = ((String) cfg.get("method")).trim().toUpperCase();
        }
        if (method.isEmpty()) method = "POST";

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20));

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
        boolean hasBody = !"GET".equals(method) && cfg.get("body") != null;
        if (hasBody) {
            String bodyRaw = cfg.get("body").toString();
            String body = HandlerUtils.renderTemplate(bodyRaw, context, extra);
            if (body != null && !body.isEmpty()) {
                boolean hasContentType = cfg.get("headers") instanceof Map
                        && ((Map<?, ?>) cfg.get("headers")).containsKey("Content-Type");
                if (!hasContentType) {
                    builder.header("Content-Type", "application/json");
                }
                builder.method(method, HttpRequest.BodyPublishers.ofString(body));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }
        } else {
            if ("GET".equals(method)) {
                builder.GET();
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }
        }

        HttpRequest request = builder.build();
        logger.debug("http_request: {} {}", method, url);
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
        context.putVariable("httpResponseStatus", response.statusCode());
        context.putVariable("httpResponseBody", response.body() != null ? response.body() : "");
        if (!success) {
            logger.warn("http_request 失败: status={}, body={}", response.statusCode(), response.body());
        }
        return success;
    }
}
