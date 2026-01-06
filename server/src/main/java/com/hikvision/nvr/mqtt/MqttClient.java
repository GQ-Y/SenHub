package com.hikvision.nvr.mqtt;

import com.hikvision.nvr.config.Config;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * MQTT客户端封装类
 */
public class MqttClient {
    private static final Logger logger = LoggerFactory.getLogger(MqttClient.class);
    private MqttAsyncClient client;
    private Config.MqttConfig config;
    private boolean connected = false;
    private Consumer<String> messageHandler;

    public MqttClient(Config.MqttConfig config) {
        this.config = config;
    }

    /**
     * 连接MQTT服务器
     */
    public boolean connect() {
        try {
            String broker = config.getBroker();
            String clientId = config.getClientId();

            client = new MqttAsyncClient(broker, clientId, new MemoryPersistence());

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setKeepAliveInterval(config.getKeepAlive());

            // 设置用户名和密码（如果提供）
            if (config.getUsername() != null && !config.getUsername().isEmpty()) {
                options.setUserName(config.getUsername());
            }
            if (config.getPassword() != null && !config.getPassword().isEmpty()) {
                options.setPassword(config.getPassword().toCharArray());
            }

            // 设置回调
            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    connected = false;
                    logger.warn("MQTT连接丢失: {}", cause.getMessage());
                    // 自动重连
                    reconnect();
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    String payload = new String(message.getPayload());
                    logger.debug("收到MQTT消息 - 主题: {}, 内容: {}", topic, payload);
                    if (messageHandler != null) {
                        messageHandler.accept(payload);
                    }
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    logger.debug("消息发送完成");
                }
            });

            // 连接
            IMqttToken token = client.connect(options);
            token.waitForCompletion();
            connected = true;
            logger.info("MQTT连接成功: {}", broker);

            // 订阅命令主题
            subscribe(config.getCommandTopic());
            return true;

        } catch (MqttException e) {
            logger.error("MQTT连接失败", e);
            connected = false;
            return false;
        }
    }

    /**
     * 订阅主题
     */
    public boolean subscribe(String topic) {
        if (!connected || client == null) {
            logger.error("MQTT未连接，无法订阅主题");
            return false;
        }

        try {
            client.subscribe(topic, config.getQos());
            logger.info("订阅主题成功: {}", topic);
            return true;
        } catch (MqttException e) {
            logger.error("订阅主题失败: {}", topic, e);
            return false;
        }
    }

    /**
     * 发布消息
     */
    public boolean publish(String topic, String message) {
        return publish(topic, message, config.getQos(), false);
    }

    /**
     * 发布消息
     */
    public boolean publish(String topic, String message, int qos, boolean retained) {
        if (!connected || client == null) {
            logger.error("MQTT未连接，无法发布消息");
            return false;
        }

        try {
            MqttMessage mqttMessage = new MqttMessage(message.getBytes());
            mqttMessage.setQos(qos);
            mqttMessage.setRetained(retained);

            IMqttDeliveryToken token = client.publish(topic, mqttMessage);
            token.waitForCompletion();
            logger.debug("消息发布成功 - 主题: {}", topic);
            return true;
        } catch (MqttException e) {
            logger.error("发布消息失败 - 主题: {}", topic, e);
            return false;
        }
    }

    /**
     * 发布状态消息
     */
    public boolean publishStatus(String message) {
        return publish(config.getStatusTopic(), message);
    }

    /**
     * 发布响应消息
     */
    public boolean publishResponse(String message) {
        return publish(config.getResponseTopic(), message);
    }

    /**
     * 设置消息处理器
     */
    public void setMessageHandler(Consumer<String> handler) {
        this.messageHandler = handler;
    }

    /**
     * 重连
     */
    private void reconnect() {
        int maxRetries = 5;
        int retryCount = 0;
        while (retryCount < maxRetries && !connected) {
            try {
                Thread.sleep(5000); // 等待5秒后重连
                logger.info("尝试重连MQTT服务器 ({}/{})", retryCount + 1, maxRetries);
                if (connect()) {
                    logger.info("MQTT重连成功");
                    return;
                }
                retryCount++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        logger.error("MQTT重连失败，已达到最大重试次数");
    }

    /**
     * 断开连接
     */
    public void disconnect() {
        if (client != null && connected) {
            try {
                client.disconnect();
                connected = false;
                logger.info("MQTT连接已断开");
            } catch (MqttException e) {
                logger.error("断开MQTT连接失败", e);
            }
        }
    }

    /**
     * 关闭客户端
     */
    public void close() {
        disconnect();
        try {
            if (client != null) {
                client.close();
            }
        } catch (MqttException e) {
            logger.error("关闭MQTT客户端失败", e);
        }
    }

    /**
     * 检查是否已连接
     */
    public boolean isConnected() {
        return connected && client != null && client.isConnected();
    }
}
