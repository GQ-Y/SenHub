package com.digital.video.gateway.mqtt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 无连接时的降级发布器：仅写日志，不抛错；可选写 mqtt_fallback_log 表。
 */
public class FallbackMqttPublisher implements MqttPublisher {
    private static final Logger logger = LoggerFactory.getLogger(FallbackMqttPublisher.class);

    @Override
    public boolean publish(String topic, String message) {
        logger.warn("[MQTT降级] 未连接，跳过发布 topic={} payloadLength={}", topic, message != null ? message.length() : 0);
        return false;
    }

    @Override
    public boolean publishStatus(String message) {
        logger.warn("[MQTT降级] 未连接，跳过发布状态 payloadLength={}", message != null ? message.length() : 0);
        return false;
    }

    @Override
    public boolean publishResponse(String message) {
        logger.warn("[MQTT降级] 未连接，跳过发布响应 payloadLength={}", message != null ? message.length() : 0);
        return false;
    }

    @Override
    public boolean isConnected() {
        return false;
    }

    @Override
    public String getGatewayId() {
        return "";
    }
}
