package com.digital.video.gateway.workflow.handlers;

import com.digital.video.gateway.workflow.FlowContext;
import com.digital.video.gateway.workflow.FlowNodeDefinition;
import com.digital.video.gateway.workflow.FlowNodeHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * MQTT 订阅节点：作为流程入口，消息到达时由外部派发执行；本 handler 仅做透传，将 payload 写入 context
 */
public class MqttSubscribeHandler implements FlowNodeHandler {
    private static final Logger logger = LoggerFactory.getLogger(MqttSubscribeHandler.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public boolean execute(FlowNodeDefinition node, FlowContext context) throws Exception {
        // 入口节点已在派发时注入 payload；此处可把 context.getPayload() 中的 device_id、command、params 等写入 variables
        Map<String, Object> payload = context.getPayload();
        if (payload != null) {
            if (payload.containsKey("device_id")) context.setDeviceId(String.valueOf(payload.get("device_id")));
            if (payload.containsKey("assembly_id")) context.setAssemblyId(String.valueOf(payload.get("assembly_id")));
            if (payload.containsKey("command")) context.putVariable("command", payload.get("command"));
            if (payload.containsKey("params")) context.putVariable("params", payload.get("params"));
        }
        return true;
    }
}
