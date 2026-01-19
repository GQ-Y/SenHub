package com.digital.video.gateway.workflow.handlers;

import com.digital.video.gateway.service.CaptureService;
import com.digital.video.gateway.workflow.FlowContext;
import com.digital.video.gateway.workflow.FlowNodeDefinition;
import com.digital.video.gateway.workflow.FlowNodeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;

/**
 * 抓图节点处理器
 */
public class CaptureHandler implements FlowNodeHandler {
    private static final Logger logger = LoggerFactory.getLogger(CaptureHandler.class);
    private final CaptureService captureService;

    public CaptureHandler(CaptureService captureService) {
        this.captureService = captureService;
    }

    @Override
    public boolean execute(FlowNodeDefinition node, FlowContext context) throws Exception {
        if (captureService == null) {
            logger.warn("CaptureService 未初始化，跳过抓图");
            return false;
        }
        if (context.getDeviceId() == null) {
            logger.warn("缺少deviceId，无法抓图");
            return false;
        }

        Map<String, Object> cfg = node.getConfig();
        int channel = 1;
        if (cfg != null && cfg.get("channel") instanceof Number) {
            channel = ((Number) cfg.get("channel")).intValue();
        }

        String path = captureService.captureSnapshot(context.getDeviceId(), channel);
        if (path != null) {
            context.putVariable("capturePath", path);
            context.putVariable("captureFileName", new File(path).getName());
            logger.info("抓图完成: deviceId={}, channel={}, path={}", context.getDeviceId(), channel, path);
            return true;
        }

        logger.warn("抓图失败: deviceId={}, channel={}", context.getDeviceId(), channel);
        return false;
    }
}
