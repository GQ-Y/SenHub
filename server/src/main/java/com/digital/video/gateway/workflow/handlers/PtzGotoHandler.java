package com.digital.video.gateway.workflow.handlers;

import com.digital.video.gateway.service.PTZService;
import com.digital.video.gateway.workflow.FlowContext;
import com.digital.video.gateway.workflow.FlowNodeDefinition;
import com.digital.video.gateway.workflow.FlowNodeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * PTZ 转到预置点或指定角度节点：报警联动预置点或与雷达联动。
 * config: presetIndex（优先）或 pan/tilt/zoom；deviceId、channel 可选。
 */
public class PtzGotoHandler implements FlowNodeHandler {
    private static final Logger logger = LoggerFactory.getLogger(PtzGotoHandler.class);
    private final PTZService ptzService;

    public PtzGotoHandler(PTZService ptzService) {
        this.ptzService = ptzService;
    }

    @Override
    public boolean execute(FlowNodeDefinition node, FlowContext context) {
        if (ptzService == null) {
            logger.warn("PTZService 未初始化，跳过 ptz_goto");
            return false;
        }
        Map<String, Object> cfg = node.getConfig();
        String targetDeviceId = null;
        if (cfg != null && cfg.get("deviceId") instanceof String) {
            String raw = (String) cfg.get("deviceId");
            targetDeviceId = HandlerUtils.renderTemplate(raw, context, context.getVariables() != null ? new java.util.HashMap<>(context.getVariables()) : null);
        }
        if (targetDeviceId == null || targetDeviceId.isBlank()) {
            targetDeviceId = context.getDeviceId();
        }
        if (targetDeviceId == null || targetDeviceId.isBlank()) {
            logger.warn("ptz_goto 缺少设备ID，跳过");
            return false;
        }

        int channel = 1;
        if (cfg != null && cfg.get("channel") instanceof Number) {
            channel = ((Number) cfg.get("channel")).intValue();
        }

        if (cfg != null && cfg.get("presetIndex") instanceof Number) {
            int presetIndex = ((Number) cfg.get("presetIndex")).intValue();
            boolean result = ptzService.gotoPreset(targetDeviceId, channel, presetIndex);
            logger.info("ptz_goto 预置点: deviceId={}, channel={}, presetIndex={}, result={}",
                    targetDeviceId, channel, presetIndex, result);
            return result;
        }

        float pan = 0;
        float tilt = 0;
        float zoom = 1.0f;
        if (cfg != null) {
            if (cfg.get("pan") instanceof Number) pan = ((Number) cfg.get("pan")).floatValue();
            if (cfg.get("tilt") instanceof Number) tilt = ((Number) cfg.get("tilt")).floatValue();
            if (cfg.get("zoom") instanceof Number) zoom = ((Number) cfg.get("zoom")).floatValue();
        }
        boolean result = ptzService.gotoAngle(targetDeviceId, channel, pan, tilt, zoom);
        logger.info("ptz_goto 角度: deviceId={}, channel={}, pan={}, tilt={}, zoom={}, result={}",
                targetDeviceId, channel, pan, tilt, zoom, result);
        return result;
    }
}
