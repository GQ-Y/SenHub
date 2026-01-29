package com.digital.video.gateway.workflow.handlers;

import com.digital.video.gateway.workflow.FlowContext;
import com.digital.video.gateway.workflow.FlowNodeDefinition;
import com.digital.video.gateway.workflow.FlowNodeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 延迟节点：等待 N 秒后继续执行（如等录像写盘后再下载、错峰上报）。
 */
public class DelayHandler implements FlowNodeHandler {
    private static final Logger logger = LoggerFactory.getLogger(DelayHandler.class);
    private static final double MAX_SECONDS = 300;

    @Override
    public boolean execute(FlowNodeDefinition node, FlowContext context) throws Exception {
        Map<String, Object> cfg = node.getConfig();
        double seconds = 0;
        if (cfg != null && cfg.get("seconds") instanceof Number) {
            seconds = ((Number) cfg.get("seconds")).doubleValue();
        }
        if (seconds <= 0) {
            logger.debug("delay 节点未配置或 seconds<=0，跳过等待");
            return true;
        }
        if (seconds > MAX_SECONDS) {
            seconds = MAX_SECONDS;
            logger.warn("delay 节点 seconds 超过上限 {}，已截断", MAX_SECONDS);
        }
        long ms = (long) (seconds * 1000);
        logger.info("delay 节点等待 {} 秒: nodeId={}", seconds, node.getNodeId());
        Thread.sleep(ms);
        return true;
    }
}
