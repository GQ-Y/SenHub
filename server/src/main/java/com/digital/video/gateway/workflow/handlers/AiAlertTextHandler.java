package com.digital.video.gateway.workflow.handlers;

import com.digital.video.gateway.service.AiGatewayClient;
import com.digital.video.gateway.workflow.FlowContext;
import com.digital.video.gateway.workflow.FlowNodeDefinition;
import com.digital.video.gateway.workflow.FlowNodeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * AI 警示语生成节点：根据报警事件类型和核验结果生成简洁中文警示语文本，写入 context.ai_alert_text。
 */
public class AiAlertTextHandler implements FlowNodeHandler {
    private static final Logger logger = LoggerFactory.getLogger(AiAlertTextHandler.class);

    private static final String SYSTEM_PROMPT = "你是一个安防播报助手。根据给定的报警事件信息，生成一句简洁的中文警示语播报文本，用于音柱播报。要求：不超过50字，直接输出文本，不要引号或多余说明。";

    private final AiGatewayClient aiClient;

    public AiAlertTextHandler(AiGatewayClient aiClient) {
        this.aiClient = aiClient;
    }

    @Override
    public boolean execute(FlowNodeDefinition node, FlowContext context) throws Exception {
        if (aiClient == null) {
            logger.warn("AiGatewayClient 未初始化，使用默认警示语");
            context.putVariable("ai_alert_text", "检测到" + (context.getAlarmType() != null ? context.getAlarmType() : "报警") + "，请注意。");
            return true;
        }

        String alarmType = context.getAlarmType() != null ? context.getAlarmType() : "报警";
        String deviceId = context.getDeviceId() != null ? context.getDeviceId() : "";
        String verifyReason = null;
        if (context.getVariables() != null && context.getVariables().get("ai_verify_reason") instanceof String) {
            verifyReason = (String) context.getVariables().get("ai_verify_reason");
        }

        StringBuilder userContent = new StringBuilder();
        userContent.append("报警事件类型：").append(alarmType);
        if (!deviceId.isEmpty()) {
            userContent.append("；设备：").append(deviceId);
        }
        if (verifyReason != null && !verifyReason.isEmpty() && !verifyReason.contains("跳过")) {
            userContent.append("；核验说明：").append(verifyReason);
        }
        userContent.append("。请生成一句播报用语。");

        Map<String, Object> cfg = node.getConfig();
        if (cfg != null && cfg.get("prompt") instanceof String) {
            String extra = ((String) cfg.get("prompt")).trim();
            if (!extra.isEmpty()) {
                userContent.append("\n附加要求：").append(extra);
            }
        }

        String model = null;
        if (cfg != null && cfg.get("model") instanceof String) {
            model = ((String) cfg.get("model")).trim();
            if (model.isEmpty()) model = null;
        }

        List<Map<String, Object>> messages = List.of(
                Map.<String, Object>of("role", "system", "content", SYSTEM_PROMPT),
                Map.<String, Object>of("role", "user", "content", userContent.toString())
        );

        try {
            String text = aiClient.chatCompletion(messages, model, 150);
            if (text != null && !text.isBlank()) {
                context.putVariable("ai_alert_text", text.trim());
                logger.info("ai_alert_text 生成: {}", text.trim());
                return true;
            }
        } catch (Exception e) {
            logger.warn("ai_alert_text 调用失败: {}", e.getMessage());
        }

        context.putVariable("ai_alert_text", "检测到" + alarmType + "，请注意。");
        return true;
    }
}
