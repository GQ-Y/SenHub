package com.digital.video.gateway.workflow.handlers;

import com.digital.video.gateway.service.AiGatewayClient;
import com.digital.video.gateway.workflow.FlowContext;
import com.digital.video.gateway.workflow.FlowNodeDefinition;
import com.digital.video.gateway.workflow.FlowNodeHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * AI 报警图片核验节点：使用 function call 约束模型输出，判断图片内容是否与报警事件一致。
 * 图片来源：captureOssUrl（优先）或 capturePath（转 base64）。最多重试 3 次，3 次失败则跳过（放行）。
 */
public class AiVerifyHandler implements FlowNodeHandler {
    private static final Logger logger = LoggerFactory.getLogger(AiVerifyHandler.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final List<Map<String, Object>> VERIFY_TOOLS = List.of(
            Map.<String, Object>of(
                    "type", "function",
                    "function", Map.of(
                            "name", "report_verify_result",
                            "description", "报告图片核验结果",
                            "parameters", Map.of(
                                    "type", "object",
                                    "properties", Map.of(
                                            "match", Map.of("type", "boolean", "description", "图片是否与报警事件一致"),
                                            "reason", Map.of("type", "string", "description", "判定理由，简短说明")
                                    ),
                                    "required", List.of("match", "reason")
                            )
                    )
            )
    );

    private static final Map<String, Object> TOOL_CHOICE = Map.of(
            "type", "function",
            "function", Map.of("name", "report_verify_result")
    );

    private static final int MAX_RETRIES = 3;
    private static final String SKIP_REASON = "核验超时跳过";

    private final AiGatewayClient aiClient;

    public AiVerifyHandler(AiGatewayClient aiClient) {
        this.aiClient = aiClient;
    }

    @Override
    public boolean execute(FlowNodeDefinition node, FlowContext context) throws Exception {
        if (aiClient == null) {
            logger.warn("AiGatewayClient 未初始化，跳过 ai_verify");
            context.putVariable("_last_node_branch", "true");
            context.putVariable("ai_verify_match", true);
            context.putVariable("ai_verify_reason", SKIP_REASON);
            return true;
        }

        String imageUrl = null;
        String base64Data = null;
        String mimeType = "image/jpeg";

        if (context.getVariables() != null) {
            Object v = context.getVariables().get("captureOssUrl");
            if (v instanceof String && !((String) v).isBlank()) {
                imageUrl = (String) v;
            }
            if (imageUrl == null) {
                v = context.getVariables().get("captureUrl");
                if (v instanceof String && !((String) v).isBlank()) {
                    imageUrl = (String) v;
                }
            }
        }

        if (imageUrl == null && context.getVariables() != null) {
            Object pathObj = context.getVariables().get("capturePath");
            if (pathObj instanceof String) {
                String path = (String) pathObj;
                try {
                    byte[] bytes = Files.readAllBytes(java.nio.file.Paths.get(path));
                    base64Data = Base64.getEncoder().encodeToString(bytes);
                    String lower = path.toLowerCase();
                    if (lower.endsWith(".png")) mimeType = "image/png";
                    else if (lower.endsWith(".gif")) mimeType = "image/gif";
                    else if (lower.endsWith(".webp")) mimeType = "image/webp";
                } catch (Exception e) {
                    logger.warn("读取抓图文件失败: {}", path, e);
                }
            }
        }

        if (imageUrl == null && base64Data == null) {
            logger.warn("ai_verify 无可用图片（captureOssUrl/captureUrl/capturePath），跳过核验");
            context.putVariable("_last_node_branch", "true");
            context.putVariable("ai_verify_match", true);
            context.putVariable("ai_verify_reason", "无图片跳过");
            return true;
        }

        String alarmType = context.getAlarmType() != null ? context.getAlarmType() : "未知";
        String userText = "当前报警事件类型为：" + alarmType + "。请根据图片内容判断：图片中的场景是否与该报警事件一致（例如入侵报警则画面中应有人员/车辆等）。";
        Map<String, Object> cfg = node.getConfig();
        if (cfg != null && cfg.get("prompt") instanceof String) {
            String extra = ((String) cfg.get("prompt")).trim();
            if (!extra.isEmpty()) {
                userText = userText + "\n附加说明：" + extra;
            }
        }

        String model = null;
        if (cfg != null && cfg.get("model") instanceof String) {
            model = ((String) cfg.get("model")).trim();
        }
        if (model == null || model.isEmpty()) {
            model = null;
        }

        List<Map<String, Object>> messages;
        if (imageUrl != null) {
            messages = List.of(
                    Map.<String, Object>of("role", "user", "content", List.of(
                            Map.of("type", "text", "text", userText),
                            Map.of("type", "image_url", "image_url", Map.of("url", imageUrl))
                    ))
            );
        } else {
            messages = List.of(
                    Map.<String, Object>of("role", "user", "content", List.of(
                            Map.of("type", "text", "text", userText),
                            Map.of("type", "image_url", "image_url", Map.of("url", "data:" + mimeType + ";base64," + base64Data))
                    ))
            );
        }

        boolean match = true;
        String reason = SKIP_REASON;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                String responseJson = aiClient.chatCompletionWithTools(messages, VERIFY_TOOLS, TOOL_CHOICE, model);
                VerifyResult result = parseToolCallResult(responseJson);
                if (result != null) {
                    match = result.match;
                    reason = result.reason != null ? result.reason : "";
                    logger.info("ai_verify 核验结果: match={}, reason={}", match, reason);
                    break;
                }
            } catch (Exception e) {
                logger.warn("ai_verify 第 {} 次请求失败: {}", attempt, e.getMessage());
            }
            if (attempt < MAX_RETRIES) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        context.putVariable("_last_node_branch", match ? "true" : "false");
        context.putVariable("ai_verify_match", match);
        context.putVariable("ai_verify_reason", reason);
        return true;
    }

    private VerifyResult parseToolCallResult(String responseJson) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> root = mapper.readValue(responseJson, Map.class);
            List<?> choices = (List<?>) root.get("choices");
            if (choices == null || choices.isEmpty()) return null;
            @SuppressWarnings("unchecked")
            Map<String, Object> first = (Map<String, Object>) choices.get(0);
            Map<String, Object> message = (Map<String, Object>) first.get("message");
            if (message == null) return null;
            List<?> toolCalls = (List<?>) message.get("tool_calls");
            if (toolCalls == null || toolCalls.isEmpty()) return null;
            @SuppressWarnings("unchecked")
            Map<String, Object> tc = (Map<String, Object>) toolCalls.get(0);
            Map<String, Object> fn = (Map<String, Object>) tc.get("function");
            if (fn == null) return null;
            String name = (String) fn.get("name");
            if (!"report_verify_result".equals(name)) return null;
            String arguments = (String) fn.get("arguments");
            if (arguments == null || arguments.isBlank()) return null;
            @SuppressWarnings("unchecked")
            Map<String, Object> args = mapper.readValue(arguments, Map.class);
            Boolean match = args.get("match") instanceof Boolean
                    ? (Boolean) args.get("match")
                    : Boolean.parseBoolean(String.valueOf(args.get("match")));
            String reason = args.get("reason") != null ? args.get("reason").toString() : "";
            return new VerifyResult(match, reason);
        } catch (Exception e) {
            logger.debug("解析 tool_calls 失败: {}", e.getMessage());
            return null;
        }
    }

    private static class VerifyResult {
        final boolean match;
        final String reason;

        VerifyResult(boolean match, String reason) {
            this.match = match;
            this.reason = reason;
        }
    }
}
