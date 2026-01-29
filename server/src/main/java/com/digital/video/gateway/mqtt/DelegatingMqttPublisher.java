package com.digital.video.gateway.mqtt;

/**
 * 委托发布器：已连接时使用主 MqttPublisher，未连接时使用降级 FallbackMqttPublisher
 */
public class DelegatingMqttPublisher implements MqttPublisher {
    private final MqttPublisher primary;
    private final MqttPublisher fallback;

    public DelegatingMqttPublisher(MqttPublisher primary, MqttPublisher fallback) {
        this.primary = primary != null ? primary : fallback;
        this.fallback = fallback != null ? fallback : new FallbackMqttPublisher();
    }

    private MqttPublisher current() {
        return primary.isConnected() ? primary : fallback;
    }

    @Override
    public boolean publish(String topic, String message) {
        return current().publish(topic, message);
    }

    @Override
    public boolean publishStatus(String message) {
        return current().publishStatus(message);
    }

    @Override
    public boolean publishResponse(String message) {
        return current().publishResponse(message);
    }

    @Override
    public boolean isConnected() {
        return primary.isConnected();
    }

    @Override
    public String getGatewayId() {
        return primary.isConnected() ? primary.getGatewayId() : fallback.getGatewayId();
    }
}
