package com.digital.video.gateway.mqtt;

import com.digital.video.gateway.config.Config;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * MQTT客户端封装类
 */
public class MqttClient {
    private static final Logger logger = LoggerFactory.getLogger(MqttClient.class);
    private MqttAsyncClient client;
    private Config.MqttConfig config;
    private boolean connected = false;
    /** 网关 ID（本机 MAC），用于 senhub/gateway/status 上下线消息 */
    private String gatewayId;
    private Consumer<String> messageHandler;
    /** 按主题派发：(topic, payload)，用于工作流 mqtt_subscribe 与命令主题区分 */
    private BiConsumer<String, String> topicMessageHandler;
    /** 连接/重连成功后调用，用于重新订阅 mqtt_subscribe 主题等 */
    private volatile Runnable onConnectedCallback;
    private volatile boolean reconnecting = false;  // 重连锁，避免并发重连
    private volatile boolean closed = false;  // 关闭标志，重连循环据此退出
    private final Object reconnectLock = new Object();
    /** 单线程重连调度器，持续重试直至连接成功或 close() */
    private volatile ScheduledExecutorService reconnectScheduler;

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

            // 网关 ID 使用本机 MAC；LWT 发布到 senhub/gateway/status
            gatewayId = getLocalMacAddress();
            if (gatewayId == null || gatewayId.isEmpty()) {
                gatewayId = config.getClientId() != null ? config.getClientId() : "gateway-unknown";
                logger.warn("无法获取本机 MAC，网关 ID 使用: {}", gatewayId);
            }
            String gatewayTopic = config.getGatewayStatusTopic();
            String lwtPayload = buildGatewayStatusPayload("offline", "connection_lost");
            options.setWill(gatewayTopic, lwtPayload.getBytes(StandardCharsets.UTF_8), config.getQos(), false);

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
                    if (topicMessageHandler != null) {
                        topicMessageHandler.accept(topic, payload);
                    } else if (messageHandler != null) {
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

            // 发布网关上线到 senhub/gateway/status
            String onlinePayload = buildGatewayStatusPayload("online", null);
            publish(gatewayTopic, onlinePayload, config.getQos(), false);

            // 订阅命令主题
            subscribe(config.getCommandTopic());
            // 连接/重连成功后执行回调（如重新订阅工作流 mqtt_subscribe 主题）
            if (onConnectedCallback != null) {
                try {
                    onConnectedCallback.run();
                } catch (Exception e) {
                    logger.warn("连接成功回调执行异常", e);
                }
            }
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
     * 设置消息处理器（仅 payload，兼容旧用法）
     */
    public void setMessageHandler(Consumer<String> handler) {
        this.messageHandler = handler;
    }

    /**
     * 设置按主题派发的消息处理器（topic, payload），优先于 setMessageHandler
     */
    public void setTopicMessageHandler(BiConsumer<String, String> handler) {
        this.topicMessageHandler = handler;
    }

    /**
     * 设置连接/重连成功后的回调（如重新订阅工作流 mqtt_subscribe 主题）
     */
    public void setOnConnectedCallback(Runnable callback) {
        this.onConnectedCallback = callback;
    }

    private static final int RECONNECT_DELAY_SECONDS = 5;

    /**
     * 重连：使用单线程调度器持续重试，直至连接成功或 close() 被调用
     */
    private void reconnect() {
        synchronized (reconnectLock) {
            if (reconnecting) {
                logger.debug("MQTT重连已在进行中，跳过本次重连请求");
                return;
            }
            reconnecting = true;
        }
        if (closed) {
            synchronized (reconnectLock) { reconnecting = false; }
            return;
        }
        if (reconnectScheduler == null) {
            reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "MQTT-Reconnect");
                t.setDaemon(true);
                return t;
            });
        }
        reconnectScheduler.schedule(this::reconnectOnce, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 执行一次重连尝试；若未关闭且未连接成功则再次调度自身
     */
    private void reconnectOnce() {
        if (closed) {
            synchronized (reconnectLock) { reconnecting = false; }
            return;
        }
        try {
            logger.info("尝试重连 MQTT 服务器");
            if (connect()) {
                logger.info("MQTT 重连成功");
                synchronized (reconnectLock) { reconnecting = false; }
                return;
            }
        } catch (Exception e) {
            logger.warn("MQTT 重连尝试异常", e);
        }
        if (closed || reconnectScheduler == null) {
            synchronized (reconnectLock) { reconnecting = false; }
            return;
        }
        reconnectScheduler.schedule(this::reconnectOnce, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
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
        closed = true;
        if (reconnectScheduler != null) {
            reconnectScheduler.shutdownNow();
            try {
                reconnectScheduler.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
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
     * 获取网关 ID（本机 MAC），供业务层写入网关上下线 payload
     */
    public String getGatewayId() {
        return gatewayId != null ? gatewayId : "";
    }

    /**
     * 构建网关上下线消息体：type=online/offline/fault, gateway_id=MAC, timestamp, reason(可选)
     */
    private String buildGatewayStatusPayload(String type, String reason) {
        long ts = System.currentTimeMillis() / 1000;
        StringBuilder sb = new StringBuilder(128);
        sb.append("{\"type\":\"").append(type).append("\",\"gateway_id\":\"")
                .append(gatewayId != null ? gatewayId : "").append("\",\"timestamp\":").append(ts)
                .append(",\"version\":\"1.0\"");
        if (reason != null && !reason.isEmpty()) {
            sb.append(",\"reason\":\"").append(reason.replace("\"", "\\\"")).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * 获取本机 MAC 地址（第一个非回环、非虚拟网卡的 MAC，格式 52:54:00:11:22:33）
     */
    private static String getLocalMacAddress() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (ni.isLoopback() || ni.isVirtual() || !ni.isUp()) continue;
                byte[] mac = ni.getHardwareAddress();
                if (mac == null || mac.length != 6) continue;
                StringBuilder sb = new StringBuilder(17);
                for (int i = 0; i < mac.length; i++) {
                    if (i > 0) sb.append(':');
                    sb.append(String.format("%02X", mac[i]));
                }
                String macStr = sb.toString();
                if (!"00:00:00:00:00:00".equals(macStr)) return macStr;
            }
        } catch (Exception e) {
            logger.debug("获取本机 MAC 失败: {}", e.getMessage());
        }
        return null;
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
