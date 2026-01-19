package com.digital.video.gateway.workflow.handlers;

import com.digital.video.gateway.service.PTZService;
import com.digital.video.gateway.workflow.FlowContext;
import com.digital.video.gateway.workflow.FlowNodeDefinition;
import com.digital.video.gateway.workflow.FlowNodeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * PTZ控制节点
 */
public class PTZControlHandler implements FlowNodeHandler {
    private static final Logger logger = LoggerFactory.getLogger(PTZControlHandler.class);
    private final PTZService ptzService;

    public PTZControlHandler(PTZService ptzService) {
        this.ptzService = ptzService;
    }

    @Override
    public boolean execute(FlowNodeDefinition node, FlowContext context) {
        if (ptzService == null) {
            logger.warn("PTZService 未初始化，跳过PTZ控制");
            return false;
        }

        Map<String, Object> cfg = node.getConfig();
        String targetDeviceId = null;
        if (cfg != null && cfg.get("deviceId") instanceof String) {
            targetDeviceId = (String) cfg.get("deviceId");
        }
        if (targetDeviceId == null) {
            targetDeviceId = context.getDeviceId();
        }
        if (targetDeviceId == null) {
            logger.warn("缺少PTZ设备ID，跳过控制");
            return false;
        }

        int channel = 1;
        float pan = 0;
        float tilt = 0;
        float zoom = 1.0f;
        if (cfg != null) {
            if (cfg.get("channel") instanceof Number) {
                channel = ((Number) cfg.get("channel")).intValue();
            }
            if (cfg.get("pan") instanceof Number) {
                pan = ((Number) cfg.get("pan")).floatValue();
            }
            if (cfg.get("tilt") instanceof Number) {
                tilt = ((Number) cfg.get("tilt")).floatValue();
            }
            if (cfg.get("zoom") instanceof Number) {
                zoom = ((Number) cfg.get("zoom")).floatValue();
            }
        }

        boolean result = ptzService.gotoAngle(targetDeviceId, channel, pan, tilt, zoom);
        logger.info("PTZ控制: deviceId={}, channel={}, pan={}, tilt={}, zoom={}, result={}",
                targetDeviceId, channel, pan, tilt, zoom, result);
        return result;
    }
}
