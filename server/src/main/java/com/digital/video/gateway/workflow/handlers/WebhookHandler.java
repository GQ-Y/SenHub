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
 * Webhook推送节点
 */
public class WebhookHandler implements FlowNodeHandler {
    private static final Logger logger = LoggerFactory.getLogger(WebhookHandler.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient;

    public WebhookHandler() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override
    public boolean execute(FlowNodeDefinition node, FlowContext context) throws Exception {
        Map<String, Object> cfg = node.getConfig();
        if (cfg == null || !(cfg.get("url") instanceof String)) {
            logger.info("Webhook未配置，跳过推送");
            return true;
        }

        String url = HandlerUtils.renderTemplate((String) cfg.get("url"), context, null);
        if (url == null || url.isBlank()) {
            logger.info("Webhook URL为空，跳过推送");
            return true;
        }
        Map<String, Object> body = new HashMap<>();
        if (context.getPayload() != null) {
            body.putAll(context.getPayload());
        }
        body.put("deviceId", context.getDeviceId());
        body.put("assemblyId", context.getAssemblyId());
        body.put("alarmType", context.getAlarmType());
        body.put("flowId", context.getFlowId());
        body.put("variables", context.getVariables());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
        if (!success) {
            logger.warn("Webhook推送失败: status={}, body={}", response.statusCode(), response.body());
        } else {
            logger.info("Webhook推送完成: status={}", response.statusCode());
        }
        return success;
    }
}
