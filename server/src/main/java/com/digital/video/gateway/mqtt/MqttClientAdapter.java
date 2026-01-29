package com.digital.video.gateway.mqtt;

/**
 * 封装 MqttClient 实现 MqttPublisher 接口
 */
public class MqttClientAdapter implements MqttPublisher {
    private final MqttClient client;

    public MqttClientAdapter(MqttClient client) {
        this.client = client;
    }

    @Override
    public boolean publish(String topic, String message) {
        return client != null && client.publish(topic, message);
    }

    @Override
    public boolean publishStatus(String message) {
        return client != null && client.publishStatus(message);
    }

    @Override
    public boolean publishResponse(String message) {
        return client != null && client.publishResponse(message);
    }

    @Override
    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    @Override
    public String getGatewayId() {
        return client != null ? client.getGatewayId() : "";
    }
}
