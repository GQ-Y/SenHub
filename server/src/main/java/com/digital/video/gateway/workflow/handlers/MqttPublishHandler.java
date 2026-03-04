package com.digital.video.gateway.workflow.handlers;

import com.digital.video.gateway.mqtt.MqttPublisher;
import com.digital.video.gateway.workflow.FlowContext;
import com.digital.video.gateway.workflow.FlowNodeDefinition;
import com.digital.video.gateway.workflow.FlowNodeHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * MQTT发布节点
 */
public class MqttPublishHandler implements FlowNodeHandler {
    private static final Logger logger = LoggerFactory.getLogger(MqttPublishHandler.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final MqttPublisher mqttPublisher;

    public MqttPublishHandler(MqttPublisher mqttPublisher) {
        this.mqttPublisher = mqttPublisher;
    }

    @Override
    public boolean execute(FlowNodeDefinition node, FlowContext context) throws Exception {
        if (mqttPublisher == null) {
            logger.info("MQTT未配置，跳过发布");
            return true;
        }
        Map<String, Object> cfg = node.getConfig();
        String topic = cfg != null && cfg.get("topic") instanceof String
                ? (String) cfg.get("topic")
                : "alarm/report/" + (context.getDeviceId() != null ? context.getDeviceId() : "unknown");

        topic = HandlerUtils.renderTemplate(topic, context, null);
        if (topic == null || topic.isBlank()) {
            topic = "alarm/report/" + (context.getDeviceId() != null ? context.getDeviceId() : "unknown");
            logger.info("MQTT主题未配置，使用默认主题: {}", topic);
        }

        Map<String, Object> payload = new HashMap<>();
        if (context.getPayload() != null) {
            payload.putAll(context.getPayload());
        }
        payload.putIfAbsent("deviceId", context.getDeviceId());
        payload.putIfAbsent("assemblyId", context.getAssemblyId());
        payload.putIfAbsent("device_id", context.getDeviceId());
        payload.putIfAbsent("assembly_id", context.getAssemblyId());
        payload.putIfAbsent("alarmType", context.getAlarmType());
        payload.put("flowId", context.getFlowId());

        // 优先使用 AI 算法库中配置的中文名称
        String alarmTypeName = (String) context.getVariables().get("alarmTypeName");
        if (alarmTypeName != null && !alarmTypeName.isEmpty()) {
            payload.put("alarmTypeName", alarmTypeName);
            payload.put("eventName", alarmTypeName);
        }

        if (context.getVariables().containsKey("captureUrl")) {
            payload.put("captureUrl", context.getVariables().get("captureUrl"));
        }
        if (context.getVariables().containsKey("ossUrl")) {
            payload.put("ossUrl", context.getVariables().get("ossUrl"));
        }

        String message = mapper.writeValueAsString(payload);
        boolean result = mqttPublisher.publish(topic, message);
        logger.info("MQTT发布: topic={}, result={}", topic, result);
        return result;
    }
}
