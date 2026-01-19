package com.digital.video.gateway.workflow.handlers;

import com.digital.video.gateway.service.SpeakerService;
import com.digital.video.gateway.workflow.FlowContext;
import com.digital.video.gateway.workflow.FlowNodeDefinition;
import com.digital.video.gateway.workflow.FlowNodeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 音柱播报节点
 */
public class SpeakerPlayHandler implements FlowNodeHandler {
    private static final Logger logger = LoggerFactory.getLogger(SpeakerPlayHandler.class);
    private final SpeakerService speakerService;

    public SpeakerPlayHandler(SpeakerService speakerService) {
        this.speakerService = speakerService;
    }

    @Override
    public boolean execute(FlowNodeDefinition node, FlowContext context) {
        if (speakerService == null) {
            logger.info("音柱未配置，跳过播报");
            return true;
        }

        Map<String, Object> cfg = node.getConfig();
        String targetDeviceId = null;
        if (cfg != null && cfg.get("deviceId") instanceof String) {
            targetDeviceId = (String) cfg.get("deviceId");
        }
        if (targetDeviceId == null && context.getVariables().get("speakerDeviceId") instanceof String) {
            targetDeviceId = (String) context.getVariables().get("speakerDeviceId");
        }

        if (targetDeviceId == null) {
            logger.info("音柱deviceId未配置，跳过播报");
            return true;
        }

        String textTemplate = cfg != null && cfg.get("text") instanceof String
                ? (String) cfg.get("text")
                : "检测到{alarmType}报警";
        String text = HandlerUtils.renderTemplate(textTemplate, context, null);

        boolean result = speakerService.playVoice(targetDeviceId, text);
        logger.info("触发音柱播报: deviceId={}, result={}", targetDeviceId, result);
        return result;
    }
}
