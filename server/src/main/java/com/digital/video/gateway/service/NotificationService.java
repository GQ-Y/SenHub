package com.digital.video.gateway.service;

import com.digital.video.gateway.config.Config;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 消息通知服务
 * 支持企业微信、钉钉、飞书Webhook消息推送
 */
public class NotificationService {
    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final HttpClient httpClient;
    private final Config.NotificationConfig notificationConfig;

    public NotificationService(Config.NotificationConfig notificationConfig) {
        this.notificationConfig = notificationConfig;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 发送报警通知到所有已启用的渠道
     */
    public void sendAlarmNotification(String title, String content, Map<String, Object> extra) {
        if (notificationConfig == null) {
            logger.debug("通知配置为空，跳过发送");
            return;
        }

        // 企业微信
        if (notificationConfig.getWechat() != null && notificationConfig.getWechat().isEnabled()) {
            sendToWechat(title, content, extra);
        }

        // 钉钉
        if (notificationConfig.getDingtalk() != null && notificationConfig.getDingtalk().isEnabled()) {
            sendToDingtalk(title, content, extra);
        }

        // 飞书
        if (notificationConfig.getFeishu() != null && notificationConfig.getFeishu().isEnabled()) {
            sendToFeishu(title, content, extra);
        }
    }

    /**
     * 发送到企业微信
     */
    public boolean sendToWechat(String title, String content, Map<String, Object> extra) {
        Config.NotificationChannel channel = notificationConfig.getWechat();
        if (channel == null || channel.getWebhookUrl() == null || channel.getWebhookUrl().isEmpty()) {
            logger.debug("企业微信未配置，跳过发送");
            return false;
        }

        try {
            // 构建消息体
            Map<String, Object> markdown = new HashMap<>();
            markdown.put("content", String.format("### %s\n%s", title, content));

            Map<String, Object> body = new HashMap<>();
            body.put("msgtype", "markdown");
            body.put("markdown", markdown);

            return sendWebhook(channel.getWebhookUrl(), body, "企业微信");
        } catch (Exception e) {
            logger.error("发送企业微信通知失败", e);
            return false;
        }
    }

    /**
     * 发送到钉钉
     */
    public boolean sendToDingtalk(String title, String content, Map<String, Object> extra) {
        Config.NotificationChannel channel = notificationConfig.getDingtalk();
        if (channel == null || channel.getWebhookUrl() == null || channel.getWebhookUrl().isEmpty()) {
            logger.debug("钉钉未配置，跳过发送");
            return false;
        }

        try {
            String webhookUrl = channel.getWebhookUrl();

            // 如果配置了加签密钥，需要计算签名
            if (channel.getSecret() != null && !channel.getSecret().isEmpty()) {
                long timestamp = System.currentTimeMillis();
                String sign = generateDingtalkSign(timestamp, channel.getSecret());
                webhookUrl = webhookUrl + "&timestamp=" + timestamp + "&sign=" + sign;
            }

            // 构建消息体
            Map<String, Object> markdown = new HashMap<>();
            markdown.put("title", title);
            markdown.put("text", String.format("### %s\n%s", title, content));

            Map<String, Object> body = new HashMap<>();
            body.put("msgtype", "markdown");
            body.put("markdown", markdown);

            return sendWebhook(webhookUrl, body, "钉钉");
        } catch (Exception e) {
            logger.error("发送钉钉通知失败", e);
            return false;
        }
    }

    /**
     * 发送到飞书
     */
    public boolean sendToFeishu(String title, String content, Map<String, Object> extra) {
        Config.NotificationChannel channel = notificationConfig.getFeishu();
        if (channel == null || channel.getWebhookUrl() == null || channel.getWebhookUrl().isEmpty()) {
            logger.debug("飞书未配置，跳过发送");
            return false;
        }

        try {
            // 构建消息体 - 使用富文本格式
            Map<String, Object> zhCn = new HashMap<>();
            zhCn.put("title", title);
            zhCn.put("content", new Object[][]{
                    {Map.of("tag", "text", "text", content)}
            });

            Map<String, Object> post = new HashMap<>();
            post.put("zh_cn", zhCn);

            Map<String, Object> contentMap = new HashMap<>();
            contentMap.put("post", post);

            Map<String, Object> body = new HashMap<>();
            body.put("msg_type", "post");
            body.put("content", contentMap);

            return sendWebhook(channel.getWebhookUrl(), body, "飞书");
        } catch (Exception e) {
            logger.error("发送飞书通知失败", e);
            return false;
        }
    }

    /**
     * 测试发送通知
     */
    public boolean testNotification(String channelType, String webhookUrl, String secret) {
        String title = "测试消息";
        String content = "这是一条测试消息，用于验证Webhook配置是否正确。\n\n时间: " + java.time.LocalDateTime.now();

        try {
            switch (channelType.toLowerCase()) {
                case "wechat":
                    return testWechat(webhookUrl);
                case "dingtalk":
                    return testDingtalk(webhookUrl, secret);
                case "feishu":
                    return testFeishu(webhookUrl);
                default:
                    logger.warn("未知的通知渠道类型: {}", channelType);
                    return false;
            }
        } catch (Exception e) {
            logger.error("测试通知发送失败", e);
            return false;
        }
    }

    private boolean testWechat(String webhookUrl) throws Exception {
        Map<String, Object> markdown = new HashMap<>();
        markdown.put("content", "### 测试消息\n这是一条测试消息，用于验证Webhook配置是否正确。");

        Map<String, Object> body = new HashMap<>();
        body.put("msgtype", "markdown");
        body.put("markdown", markdown);

        return sendWebhook(webhookUrl, body, "企业微信测试");
    }

    private boolean testDingtalk(String webhookUrl, String secret) throws Exception {
        String url = webhookUrl;
        if (secret != null && !secret.isEmpty()) {
            long timestamp = System.currentTimeMillis();
            String sign = generateDingtalkSign(timestamp, secret);
            url = webhookUrl + "&timestamp=" + timestamp + "&sign=" + sign;
        }

        Map<String, Object> markdown = new HashMap<>();
        markdown.put("title", "测试消息");
        markdown.put("text", "### 测试消息\n这是一条测试消息，用于验证Webhook配置是否正确。");

        Map<String, Object> body = new HashMap<>();
        body.put("msgtype", "markdown");
        body.put("markdown", markdown);

        return sendWebhook(url, body, "钉钉测试");
    }

    private boolean testFeishu(String webhookUrl) throws Exception {
        Map<String, Object> zhCn = new HashMap<>();
        zhCn.put("title", "测试消息");
        zhCn.put("content", new Object[][]{
                {Map.of("tag", "text", "text", "这是一条测试消息，用于验证Webhook配置是否正确。")}
        });

        Map<String, Object> post = new HashMap<>();
        post.put("zh_cn", zhCn);

        Map<String, Object> contentMap = new HashMap<>();
        contentMap.put("post", post);

        Map<String, Object> body = new HashMap<>();
        body.put("msg_type", "post");
        body.put("content", contentMap);

        return sendWebhook(webhookUrl, body, "飞书测试");
    }

    /**
     * 发送Webhook请求
     */
    private boolean sendWebhook(String url, Map<String, Object> body, String channelName) {
        try {
            String jsonBody = mapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                logger.info("{}通知发送成功", channelName);
                return true;
            } else {
                logger.warn("{}通知发送失败: status={}, body={}", channelName, response.statusCode(), response.body());
                return false;
            }
        } catch (Exception e) {
            logger.error("{}通知发送异常", channelName, e);
            return false;
        }
    }

    /**
     * 生成钉钉签名
     */
    private String generateDingtalkSign(long timestamp, String secret) throws Exception {
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        return URLEncoder.encode(Base64.getEncoder().encodeToString(signData), StandardCharsets.UTF_8);
    }
}
