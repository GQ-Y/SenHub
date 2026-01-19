package com.digital.video.gateway.workflow.handlers;

import com.digital.video.gateway.config.Config;
import com.digital.video.gateway.service.ConfigService;
import com.digital.video.gateway.workflow.FlowContext;
import com.digital.video.gateway.workflow.FlowNodeDefinition;
import com.digital.video.gateway.workflow.FlowNodeHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Webhook推送节点
 * 优先使用节点配置的URL，如果未配置则回退到数据库中的全局通知配置
 */
public class WebhookHandler implements FlowNodeHandler {
    private static final Logger logger = LoggerFactory.getLogger(WebhookHandler.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient;
    private ConfigService configService;

    public WebhookHandler() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }
    
    public WebhookHandler(ConfigService configService) {
        this();
        this.configService = configService;
    }
    
    // 保留旧构造函数用于兼容
    public WebhookHandler(Config.NotificationConfig notificationConfig) {
        this();
    }
    
    public void setConfigService(ConfigService configService) {
        this.configService = configService;
    }

    @Override
    public boolean execute(FlowNodeDefinition node, FlowContext context) throws Exception {
        Map<String, Object> cfg = node.getConfig();
        String url = null;
        String channelType = null;  // 渠道类型：wechat, dingtalk, feishu, 或 null（通用）
        
        // 1. 优先使用节点配置的URL
        if (cfg != null && cfg.get("url") instanceof String) {
            String nodeUrl = (String) cfg.get("url");
            if (nodeUrl != null && !nodeUrl.isBlank()) {
                url = HandlerUtils.renderTemplate(nodeUrl, context, null);
                channelType = null;  // 节点配置的URL使用通用格式或自动识别
            }
        }
        
        // 2. 如果节点未配置，尝试从数据库读取全局通知配置
        if ((url == null || url.isBlank()) && configService != null) {
            Config.NotificationConfig notificationConfig = configService.getConfig().getNotification();
            if (notificationConfig != null) {
                // 按优先级尝试：企业微信 > 钉钉 > 飞书
                if (notificationConfig.getWechat() != null && notificationConfig.getWechat().isEnabled()) {
                    url = notificationConfig.getWechat().getWebhookUrl();
                    channelType = "wechat";
                    logger.info("使用数据库全局配置-企业微信Webhook");
                } else if (notificationConfig.getDingtalk() != null && notificationConfig.getDingtalk().isEnabled()) {
                    url = notificationConfig.getDingtalk().getWebhookUrl();
                    channelType = "dingtalk";
                    logger.info("使用数据库全局配置-钉钉Webhook");
                } else if (notificationConfig.getFeishu() != null && notificationConfig.getFeishu().isEnabled()) {
                    url = notificationConfig.getFeishu().getWebhookUrl();
                    channelType = "feishu";
                    logger.info("使用数据库全局配置-飞书Webhook");
                }
            }
        }
        
        if (url == null || url.isBlank()) {
            logger.info("Webhook未配置（节点配置和全局配置均为空），跳过推送");
            return true;
        }
        
        // 构建消息体，根据URL类型适配不同格式
        String jsonBody = buildMessageBody(url, channelType, context);
        
        logger.debug("Webhook推送URL: {}", url);
        logger.debug("Webhook消息体: {}", jsonBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
        
        // 企业微信等返回200但可能有errcode
        if (success && response.body() != null) {
            try {
                Map<String, Object> respBody = mapper.readValue(response.body(), Map.class);
                Object errcode = respBody.get("errcode");
                if (errcode != null && !errcode.equals(0) && !errcode.equals("0")) {
                    logger.warn("Webhook返回错误: errcode={}, errmsg={}", errcode, respBody.get("errmsg"));
                    success = false;
                }
            } catch (Exception e) {
                // 忽略解析错误
            }
        }
        
        if (!success) {
            logger.warn("Webhook推送失败: status={}, body={}", response.statusCode(), response.body());
        } else {
            logger.info("Webhook推送完成: status={}, response={}", response.statusCode(), response.body());
        }
        return success;
    }
    
    /**
     * 根据渠道类型构建消息体
     */
    private String buildMessageBody(String url, String channelType, FlowContext context) throws Exception {
        // 构建报警信息文本
        String deviceId = context.getDeviceId() != null ? context.getDeviceId() : "未知设备";
        String alarmType = context.getAlarmType() != null ? context.getAlarmType() : "未知类型";
        String captureUrl = context.getVariables().get("captureUrl") instanceof String 
                ? (String) context.getVariables().get("captureUrl") : null;
        String ossUrl = context.getVariables().get("ossUrl") instanceof String 
                ? (String) context.getVariables().get("ossUrl") : null;
        
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String timeStr = now.format(formatter);
        
        StringBuilder content = new StringBuilder();
        content.append("【报警通知】\n");
        content.append("设备ID: ").append(deviceId).append("\n");
        content.append("报警类型: ").append(alarmType).append("\n");
        content.append("报警时间: ").append(timeStr);
        
        if (ossUrl != null && !ossUrl.isEmpty()) {
            content.append("\n抓图地址: ").append(ossUrl);
        } else if (captureUrl != null && !captureUrl.isEmpty()) {
            content.append("\n抓图地址: ").append(captureUrl);
        }
        
        String message = content.toString();
        
        // 判断渠道类型，构建对应格式
        if ("wechat".equals(channelType) || url.contains("qyapi.weixin.qq.com")) {
            // 企业微信机器人格式
            Map<String, Object> body = new HashMap<>();
            body.put("msgtype", "text");
            Map<String, Object> text = new HashMap<>();
            text.put("content", message);
            body.put("text", text);
            return mapper.writeValueAsString(body);
            
        } else if ("dingtalk".equals(channelType) || url.contains("oapi.dingtalk.com")) {
            // 钉钉机器人格式
            Map<String, Object> body = new HashMap<>();
            body.put("msgtype", "text");
            Map<String, Object> text = new HashMap<>();
            text.put("content", message);
            body.put("text", text);
            return mapper.writeValueAsString(body);
            
        } else if ("feishu".equals(channelType) || url.contains("open.feishu.cn")) {
            // 飞书机器人格式
            Map<String, Object> body = new HashMap<>();
            body.put("msg_type", "text");
            Map<String, Object> contentMap = new HashMap<>();
            contentMap.put("text", message);
            body.put("content", contentMap);
            return mapper.writeValueAsString(body);
            
        } else {
            // 通用格式（原始格式）
            Map<String, Object> body = new HashMap<>();
            if (context.getPayload() != null) {
                body.putAll(context.getPayload());
            }
            body.put("deviceId", deviceId);
            body.put("assemblyId", context.getAssemblyId());
            body.put("alarmType", alarmType);
            body.put("flowId", context.getFlowId());
            body.put("message", message);
            body.put("timestamp", timeStr);
            body.put("variables", context.getVariables());
            return mapper.writeValueAsString(body);
        }
    }
}
