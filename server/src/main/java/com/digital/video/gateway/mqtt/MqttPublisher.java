package com.digital.video.gateway.mqtt;

/**
 * MQTT 发布接口，供业务层依赖；连接失败时可降级到 FallbackMqttPublisher（写日志或落库）。
 */
public interface MqttPublisher {

    /**
     * 向指定主题发布消息
     */
    boolean publish(String topic, String message);

    /**
     * 发布设备/雷达状态（主题由配置决定，如 senhub/device/status）
     */
    boolean publishStatus(String message);

    /**
     * 发布命令响应（主题由配置决定，如 senhub/response）
     */
    boolean publishResponse(String message);

    /**
     * 是否已连接 Broker
     */
    boolean isConnected();

    /**
     * 获取网关 ID（本机 MAC），供网关上下线 payload 使用
     */
    String getGatewayId();
}
