package com.digital.video.gateway.mqtt;

import com.digital.video.gateway.config.Config;
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
    private volatile boolean reconnecting = false;  // 重连锁，避免并发重连
    private final Object reconnectLock = new Object();  // 重连同步锁

    public MqttClient(Config.MqttConfig config) {
        this.config = config;
    }

    /**
     * 连接MQTT服务器
     */
    public boolean connect() {
        String broker = null;
        try {
            broker = config.getBroker();
            String clientId = config.getClientId();
            
            // 验证配置
            if (broker == null || broker.isEmpty()) {
                logger.error("MQTT broker地址未配置");
                return false;
            }
            if (clientId == null || clientId.isEmpty()) {
                logger.error("MQTT clientId未配置");
                return false;
            }
            
            logger.debug("尝试连接MQTT服务器: broker={}, clientId={}", broker, clientId);
            
            // 预解析DNS，提前发现DNS问题
            try {
                String host = extractHostFromBroker(broker);
                if (host != null && !host.isEmpty()) {
                    java.net.InetAddress.getByName(host);
                    logger.debug("DNS解析成功: {}", host);
                }
            } catch (java.net.UnknownHostException e) {
                logger.error("MQTT连接失败：DNS预解析失败 - 无法解析主机名: {}，错误: {}", extractHostFromBroker(broker), e.getMessage());
                logger.error("请检查：1) 网络连接是否正常 2) DNS服务器配置是否正确 3) 主机名是否正确");
                connected = false;
                return false;
            } catch (Exception e) {
                logger.warn("DNS预解析警告: {}", e.getMessage());
                // 继续尝试连接，可能只是警告
            }

            client = new MqttAsyncClient(broker, clientId, new MemoryPersistence());

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            // 设置KeepAlive，至少60秒，避免频繁断开
            int keepAlive = Math.max(config.getKeepAlive(), 60);
            options.setKeepAliveInterval(keepAlive);
            // 设置自动重连（但我们会手动处理重连逻辑）
            options.setAutomaticReconnect(false);
            // 设置连接超时
            options.setConnectionTimeout(30);

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
                    // 先检查是否已经在重连中，避免重复触发
                    synchronized (reconnectLock) {
                        if (reconnecting) {
                            logger.debug("MQTT连接丢失，但重连已在进行中，忽略本次连接丢失事件");
                            return;
                        }
                    }
                    
                    connected = false;
                    String reason = cause != null ? cause.getMessage() : "未知原因";
                    logger.warn("MQTT连接丢失: {}", reason);
                    
                    // 自动重连（使用同步锁避免并发重连）
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
            synchronized (reconnectLock) {
                reconnecting = false;  // 重置重连标志
            }
            logger.info("MQTT连接成功: {}", broker);

            // 订阅命令主题
            subscribe(config.getCommandTopic());
            return true;

        } catch (MqttException e) {
            // 详细记录错误信息，包括根本原因
            Throwable cause = e.getCause();
            if (cause != null) {
                if (cause instanceof java.net.UnknownHostException) {
                    logger.error("MQTT连接失败：DNS解析失败 - 无法解析主机名: {}，错误: {}", broker, cause.getMessage());
                    logger.error("请检查：1) 网络连接是否正常 2) DNS服务器配置是否正确 3) 主机名是否正确");
                } else if (cause instanceof java.net.ConnectException) {
                    logger.error("MQTT连接失败：无法连接到服务器 - {}，错误: {}", broker, cause.getMessage());
                    logger.error("请检查：1) MQTT服务器是否运行 2) 端口是否正确 3) 防火墙是否阻止连接");
                } else if (cause instanceof java.net.SocketTimeoutException) {
                    logger.error("MQTT连接失败：连接超时 - {}，错误: {}", broker, cause.getMessage());
                    logger.error("请检查：1) 网络延迟是否过高 2) 连接超时设置是否合理 3) 服务器是否可访问");
                } else {
                    logger.error("MQTT连接失败：{}，根本原因: {} - {}", broker, cause.getClass().getSimpleName(), cause.getMessage());
                }
            } else {
                logger.error("MQTT连接失败：{}，错误码: {}，错误消息: {}", broker, e.getReasonCode(), e.getMessage());
            }
            logger.debug("MQTT连接异常堆栈", e);
            connected = false;
            return false;
        } catch (Exception e) {
            // 捕获其他可能的异常（如NullPointerException等）
            String brokerStr = broker != null ? broker : (config != null ? config.getBroker() : "未知");
            logger.error("MQTT连接失败：发生未预期的异常 - {}，错误: {}", brokerStr, e.getMessage());
            logger.debug("MQTT连接异常堆栈", e);
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
        synchronized (reconnectLock) {
            if (reconnecting) {
                logger.debug("MQTT重连已在进行中，跳过本次重连请求");
                return;
            }
            reconnecting = true;
        }
        
        // 在后台线程中执行重连，避免阻塞回调线程
        new Thread(() -> {
            try {
                int maxRetries = 5;
                int retryCount = 0;
                while (retryCount < maxRetries && !connected) {
                    try {
                        Thread.sleep(5000); // 等待5秒后重连
                        logger.info("尝试重连MQTT服务器 ({}/{})", retryCount + 1, maxRetries);
                        if (connect()) {
                            logger.info("MQTT重连成功");
                            synchronized (reconnectLock) {
                                reconnecting = false;
                            }
                            return;
                        }
                        retryCount++;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        synchronized (reconnectLock) {
                            reconnecting = false;
                        }
                        return;
                    }
                }
                logger.error("MQTT重连失败，已达到最大重试次数");
                synchronized (reconnectLock) {
                    reconnecting = false;
                }
            } catch (Exception e) {
                logger.error("MQTT重连过程异常", e);
                synchronized (reconnectLock) {
                    reconnecting = false;
                }
            }
        }, "MQTT Reconnect Thread").start();
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
    
    /**
     * 从broker URL提取主机名
     */
    private String extractHostFromBroker(String broker) {
        if (broker == null || broker.isEmpty()) {
            return null;
        }
        // tcp://host:port 格式
        if (broker.startsWith("tcp://")) {
            String withoutProtocol = broker.substring(6);
            int colonIndex = withoutProtocol.indexOf(':');
            if (colonIndex > 0) {
                return withoutProtocol.substring(0, colonIndex);
            }
            return withoutProtocol;
        }
        // ssl://host:port 格式
        if (broker.startsWith("ssl://")) {
            String withoutProtocol = broker.substring(6);
            int colonIndex = withoutProtocol.indexOf(':');
            if (colonIndex > 0) {
                return withoutProtocol.substring(0, colonIndex);
            }
            return withoutProtocol;
        }
        return broker;
    }
}
