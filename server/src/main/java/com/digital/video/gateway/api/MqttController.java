package com.digital.video.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.digital.video.gateway.config.Config;
import com.digital.video.gateway.mqtt.MqttClient;
import com.digital.video.gateway.service.ConfigService;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * MQTT配置控制器
 */
public class MqttController {
    private static final Logger logger = LoggerFactory.getLogger(MqttController.class);
    private final ConfigService configService;
    private final MqttClient mqttClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MqttController(ConfigService configService, MqttClient mqttClient) {
        this.configService = configService;
        this.mqttClient = mqttClient;
    }

    public void getConfig(Context ctx) {
        try {
            Config.MqttConfig mqttConfig = configService.getConfig().getMqtt();

            Map<String, Object> config = new HashMap<>();
            config.put("host", extractHost(mqttConfig.getBroker()));
            config.put("port", extractPort(mqttConfig.getBroker()));
            config.put("clientId", mqttConfig.getClientId());
            config.put("username", mqttConfig.getUsername());
            config.put("password", mqttConfig.getPassword() != null && !mqttConfig.getPassword().isEmpty() ? "******" : "");
            config.put("topicStatus", mqttConfig.getStatusTopic());
            config.put("topicCommand", mqttConfig.getCommandTopic());
            config.put("qos", mqttConfig.getQos());
            config.put("connected", mqttClient.isConnected());

            ctx.status(200);
            ctx.contentType("application/json");
            ctx.result(createSuccessResponse(config));
        } catch (Exception e) {
            logger.error("获取MQTT配置失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "获取MQTT配置失败: " + e.getMessage()));
        }
    }

    public void updateConfig(Context ctx) {
        try {
            Map<String, Object> body = objectMapper.readValue(ctx.body(), Map.class);

            Config config = configService.getConfig();
            Config.MqttConfig mqttConfig = config.getMqtt();

            if (mqttConfig == null) {
                mqttConfig = new Config.MqttConfig();
            }

            String host = (String) body.get("host");
            String port = String.valueOf(body.get("port"));
            if (host != null && port != null) {
                mqttConfig.setBroker("tcp://" + host + ":" + port);
            }

            if (body.containsKey("clientId")) {
                mqttConfig.setClientId((String) body.get("clientId"));
            }
            if (body.containsKey("username")) {
                mqttConfig.setUsername((String) body.get("username"));
            }
            if (body.containsKey("password")) {
                String password = (String) body.get("password");
                if (password != null && !password.equals("******")) {
                    mqttConfig.setPassword(password);
                }
            }
            if (body.containsKey("topicStatus")) {
                mqttConfig.setStatusTopic((String) body.get("topicStatus"));
            }
            if (body.containsKey("topicCommand")) {
                mqttConfig.setCommandTopic((String) body.get("topicCommand"));
            }
            if (body.containsKey("qos")) {
                mqttConfig.setQos(((Number) body.get("qos")).intValue());
            }

            config.setMqtt(mqttConfig);
            configService.updateConfig(config);

            Map<String, Object> result = new HashMap<>();
            result.put("host", extractHost(mqttConfig.getBroker()));
            result.put("port", extractPort(mqttConfig.getBroker()));
            result.put("clientId", mqttConfig.getClientId());
            result.put("username", mqttConfig.getUsername());
            result.put("password", "******");
            result.put("topicStatus", mqttConfig.getStatusTopic());
            result.put("topicCommand", mqttConfig.getCommandTopic());
            result.put("qos", mqttConfig.getQos());
            result.put("message", "配置已更新，需要重启服务或重新连接MQTT");

            ctx.status(200);
            ctx.contentType("application/json");
            ctx.result(createSuccessResponse(result));
        } catch (Exception e) {
            logger.error("更新MQTT配置失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "更新MQTT配置失败: " + e.getMessage()));
        }
    }

    public void testConnection(Context ctx) {
        try {
            boolean connected = mqttClient.isConnected();

            Map<String, Object> result = new HashMap<>();
            result.put("connected", connected);
            result.put("message", connected ? "连接成功" : "连接失败");

            ctx.status(200);
            ctx.contentType("application/json");
            ctx.result(createSuccessResponse(result));
        } catch (Exception e) {
            logger.error("测试MQTT连接失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "测试连接失败: " + e.getMessage()));
        }
    }

    private String extractHost(String broker) {
        if (broker == null) return "";
        if (broker.startsWith("tcp://")) {
            String withoutProtocol = broker.substring(6);
            int colonIndex = withoutProtocol.indexOf(':');
            if (colonIndex > 0) return withoutProtocol.substring(0, colonIndex);
            return withoutProtocol;
        }
        return broker;
    }

    private String extractPort(String broker) {
        if (broker == null) return "1883";
        if (broker.startsWith("tcp://")) {
            String withoutProtocol = broker.substring(6);
            int colonIndex = withoutProtocol.indexOf(':');
            if (colonIndex > 0 && colonIndex < withoutProtocol.length() - 1) {
                return withoutProtocol.substring(colonIndex + 1);
            }
        }
        return "1883";
    }

    private String createSuccessResponse(Object data) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "success");
            response.put("data", data);
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            logger.error("创建响应失败", e);
            return "{\"code\":500,\"message\":\"Internal error\",\"data\":null}";
        }
    }

    private String createErrorResponse(int code, String message) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("code", code);
            response.put("message", message);
            response.put("data", null);
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            logger.error("创建错误响应失败", e);
            return "{\"code\":" + code + ",\"message\":\"" + message + "\",\"data\":null}";
        }
    }
}
