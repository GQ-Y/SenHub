package com.digital.video.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.digital.video.gateway.config.Config;
import com.digital.video.gateway.mqtt.MqttClient;
import com.digital.video.gateway.service.ConfigService;
import com.digital.video.gateway.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统配置控制器
 */
public class SystemController {
    private static final Logger logger = LoggerFactory.getLogger(SystemController.class);
    private final ConfigService configService;
    private final MqttClient mqttClient;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private com.digital.video.gateway.service.AiAnalysisService aiAnalysisService;

    public void setAiAnalysisService(com.digital.video.gateway.service.AiAnalysisService aiAnalysisService) {
        this.aiAnalysisService = aiAnalysisService;
    }

    public SystemController(ConfigService configService) {
        this.configService = configService;
        this.mqttClient = null;
        this.notificationService = null;
    }

    public SystemController(ConfigService configService, MqttClient mqttClient) {
        this.configService = configService;
        this.mqttClient = mqttClient;
        Config config = configService.getConfig();
        this.notificationService = config.getNotification() != null 
            ? new NotificationService(config.getNotification()) 
            : null;
    }

    /**
     * 获取系统配置
     * GET /api/system/config
     */
    public String getConfig(Request request, Response response) {
        try {
            Config config = configService.getConfig();
            
            Map<String, Object> systemConfig = new HashMap<>();
            
            // Scanner配置
            if (config.getScanner() != null) {
                Map<String, Object> scanner = new HashMap<>();
                scanner.put("enabled", config.getScanner().isEnabled());
                scanner.put("interval", config.getScanner().getInterval());
                scanner.put("ports", "80, 8000, 554, 37777");
                scanner.put("scanSegment", config.getScanner().getScanSegment());
                scanner.put("scanRangeStart", config.getScanner().getScanRangeStart());
                scanner.put("scanRangeEnd", config.getScanner().getScanRangeEnd());
                systemConfig.put("scanner", scanner);
            }
            
            // Auth配置（包含品牌预设）
            if (config.getDevice() != null) {
                Map<String, Object> auth = new HashMap<>();
                auth.put("timeout", config.getDevice().getLoginTimeout() * 1000); // 转换为毫秒
                
                // 品牌预设
                Map<String, Object> presets = new HashMap<>();
                
                Config.BrandPreset hik = config.getDevice().getHikvision();
                if (hik != null) {
                    Map<String, Object> hikPreset = new HashMap<>();
                    hikPreset.put("port", hik.getPort());
                    hikPreset.put("username", hik.getUsername());
                    hikPreset.put("password", hik.getPassword());
                    presets.put("hikvision", hikPreset);
                }
                
                Config.BrandPreset tiandy = config.getDevice().getTiandy();
                if (tiandy != null) {
                    Map<String, Object> tiandyPreset = new HashMap<>();
                    tiandyPreset.put("port", tiandy.getPort());
                    tiandyPreset.put("username", tiandy.getUsername());
                    tiandyPreset.put("password", tiandy.getPassword());
                    presets.put("tiandy", tiandyPreset);
                }
                
                Config.BrandPreset dahua = config.getDevice().getDahua();
                if (dahua != null) {
                    Map<String, Object> dahuaPreset = new HashMap<>();
                    dahuaPreset.put("port", dahua.getPort());
                    dahuaPreset.put("username", dahua.getUsername());
                    dahuaPreset.put("password", dahua.getPassword());
                    presets.put("dahua", dahuaPreset);
                }
                
                auth.put("presets", presets);
                systemConfig.put("auth", auth);
            }
            
            // Keeper配置
            if (config.getKeeper() != null) {
                Map<String, Object> keeper = new HashMap<>();
                keeper.put("enabled", config.getKeeper().isEnabled());
                keeper.put("checkInterval", config.getKeeper().getCheckInterval());
                systemConfig.put("keeper", keeper);
            }
            
            // OSS配置
            if (config.getOss() != null) {
                Map<String, Object> oss = new HashMap<>();
                oss.put("enabled", config.getOss().isEnabled());
                oss.put("type", config.getOss().getType());
                oss.put("endpoint", config.getOss().getEndpoint());
                oss.put("bucket", config.getOss().getBucketName());
                oss.put("accessKeyId", config.getOss().getAccessKeyId());
                oss.put("accessKeySecret", ""); // 不返回密钥
                systemConfig.put("oss", oss);
            }
            
            // Log配置
            if (config.getLog() != null) {
                Map<String, Object> log = new HashMap<>();
                log.put("level", config.getLog().getLevel());
                log.put("retentionDays", config.getLog().getMaxAge());
                systemConfig.put("log", log);
            }
            
            // Notification配置
            if (config.getNotification() != null) {
                Map<String, Object> notification = new HashMap<>();
                
                Config.NotificationChannel wechat = config.getNotification().getWechat();
                if (wechat != null) {
                    Map<String, Object> wechatConfig = new HashMap<>();
                    wechatConfig.put("enabled", wechat.isEnabled());
                    wechatConfig.put("webhookUrl", wechat.getWebhookUrl());
                    notification.put("wechat", wechatConfig);
                }
                
                Config.NotificationChannel dingtalk = config.getNotification().getDingtalk();
                if (dingtalk != null) {
                    Map<String, Object> dingtalkConfig = new HashMap<>();
                    dingtalkConfig.put("enabled", dingtalk.isEnabled());
                    dingtalkConfig.put("webhookUrl", dingtalk.getWebhookUrl());
                    dingtalkConfig.put("secret", dingtalk.getSecret());
                    notification.put("dingtalk", dingtalkConfig);
                }
                
                Config.NotificationChannel feishu = config.getNotification().getFeishu();
                if (feishu != null) {
                    Map<String, Object> feishuConfig = new HashMap<>();
                    feishuConfig.put("enabled", feishu.isEnabled());
                    feishuConfig.put("webhookUrl", feishu.getWebhookUrl());
                    notification.put("feishu", feishuConfig);
                }
                
                systemConfig.put("notification", notification);
            }

            // AI 服务配置（apiKey/ttsApiKey 脱敏返回）
            if (config.getAi() != null) {
                Config.AiConfig ai = config.getAi();
                Map<String, Object> aiMap = new HashMap<>();
                aiMap.put("enabled", ai.isEnabled());
                aiMap.put("provider", ai.getProvider());
                aiMap.put("baseUrl", ai.getBaseUrl());
                aiMap.put("apiKey", maskApiKey(ai.getApiKey()));
                aiMap.put("defaultModel", ai.getDefaultModel());
                aiMap.put("ttsProvider", ai.getTtsProvider());
                aiMap.put("ttsApiKey", maskApiKey(ai.getTtsApiKey()));
                aiMap.put("ttsGroupId", ai.getTtsGroupId());
                aiMap.put("ttsModel", ai.getTtsModel());
                aiMap.put("ttsVoice", ai.getTtsVoice());
                systemConfig.put("ai", aiMap);
            }
            
            response.status(200);
            response.type("application/json");
            return createSuccessResponse(systemConfig);
        } catch (Exception e) {
            logger.error("获取系统配置失败", e);
            response.status(500);
            return createErrorResponse(500, "获取系统配置失败: " + e.getMessage());
        }
    }

    /**
     * 更新系统配置
     * PUT /api/system/config
     */
    @SuppressWarnings("unchecked")
    public String updateConfig(Request request, Response response) {
        try {
            Map<String, Object> body = objectMapper.readValue(request.body(), Map.class);
            Config config = configService.getConfig();
            
            // 更新Scanner配置
            if (body.containsKey("scanner")) {
                Map<String, Object> scannerData = (Map<String, Object>) body.get("scanner");
                if (config.getScanner() != null) {
                    if (scannerData.containsKey("enabled")) {
                        config.getScanner().setEnabled((Boolean) scannerData.get("enabled"));
                    }
                    if (scannerData.containsKey("interval")) {
                        config.getScanner().setInterval(((Number) scannerData.get("interval")).intValue());
                    }
                    if (scannerData.containsKey("scanSegment")) {
                        config.getScanner().setScanSegment((String) scannerData.get("scanSegment"));
                    }
                    if (scannerData.containsKey("scanRangeStart")) {
                        config.getScanner().setScanRangeStart(((Number) scannerData.get("scanRangeStart")).intValue());
                    }
                    if (scannerData.containsKey("scanRangeEnd")) {
                        config.getScanner().setScanRangeEnd(((Number) scannerData.get("scanRangeEnd")).intValue());
                    }
                }
            }
            
            // 更新Auth配置（包含品牌预设）
            if (body.containsKey("auth")) {
                Map<String, Object> authData = (Map<String, Object>) body.get("auth");
                if (config.getDevice() != null) {
                    if (authData.containsKey("timeout")) {
                        int timeout = ((Number) authData.get("timeout")).intValue();
                        config.getDevice().setLoginTimeout(timeout / 1000); // 转换为秒
                    }
                    
                    // 更新品牌预设
                    if (authData.containsKey("presets")) {
                        Map<String, Object> presets = (Map<String, Object>) authData.get("presets");
                        
                        if (presets.containsKey("hikvision")) {
                            Map<String, Object> hikData = (Map<String, Object>) presets.get("hikvision");
                            Config.BrandPreset hik = config.getDevice().getHikvision();
                            if (hik == null) {
                                hik = new Config.BrandPreset(8000, "admin", "");
                                config.getDevice().setHikvision(hik);
                            }
                            if (hikData.containsKey("port")) hik.setPort(((Number) hikData.get("port")).intValue());
                            if (hikData.containsKey("username")) hik.setUsername((String) hikData.get("username"));
                            if (hikData.containsKey("password")) hik.setPassword((String) hikData.get("password"));
                        }
                        
                        if (presets.containsKey("tiandy")) {
                            Map<String, Object> tiandyData = (Map<String, Object>) presets.get("tiandy");
                            Config.BrandPreset tiandy = config.getDevice().getTiandy();
                            if (tiandy == null) {
                                tiandy = new Config.BrandPreset(8000, "Admin", "");
                                config.getDevice().setTiandy(tiandy);
                            }
                            if (tiandyData.containsKey("port")) tiandy.setPort(((Number) tiandyData.get("port")).intValue());
                            if (tiandyData.containsKey("username")) tiandy.setUsername((String) tiandyData.get("username"));
                            if (tiandyData.containsKey("password")) tiandy.setPassword((String) tiandyData.get("password"));
                        }
                        
                        if (presets.containsKey("dahua")) {
                            Map<String, Object> dahuaData = (Map<String, Object>) presets.get("dahua");
                            Config.BrandPreset dahua = config.getDevice().getDahua();
                            if (dahua == null) {
                                dahua = new Config.BrandPreset(37777, "admin", "");
                                config.getDevice().setDahua(dahua);
                            }
                            if (dahuaData.containsKey("port")) dahua.setPort(((Number) dahuaData.get("port")).intValue());
                            if (dahuaData.containsKey("username")) dahua.setUsername((String) dahuaData.get("username"));
                            if (dahuaData.containsKey("password")) dahua.setPassword((String) dahuaData.get("password"));
                        }
                    }
                }
            }
            
            // 更新Keeper配置
            if (body.containsKey("keeper")) {
                Map<String, Object> keeperData = (Map<String, Object>) body.get("keeper");
                if (config.getKeeper() != null) {
                    if (keeperData.containsKey("enabled")) {
                        config.getKeeper().setEnabled((Boolean) keeperData.get("enabled"));
                    }
                    if (keeperData.containsKey("checkInterval")) {
                        config.getKeeper().setCheckInterval(((Number) keeperData.get("checkInterval")).intValue());
                    }
                }
            }
            
            // 更新OSS配置
            if (body.containsKey("oss")) {
                Map<String, Object> ossData = (Map<String, Object>) body.get("oss");
                if (config.getOss() != null) {
                    if (ossData.containsKey("enabled")) {
                        config.getOss().setEnabled((Boolean) ossData.get("enabled"));
                    }
                    if (ossData.containsKey("type")) {
                        config.getOss().setType((String) ossData.get("type"));
                    }
                    if (ossData.containsKey("endpoint")) {
                        config.getOss().setEndpoint((String) ossData.get("endpoint"));
                    }
                    if (ossData.containsKey("bucket")) {
                        config.getOss().setBucketName((String) ossData.get("bucket"));
                    }
                    if (ossData.containsKey("accessKeyId")) {
                        config.getOss().setAccessKeyId((String) ossData.get("accessKeyId"));
                    }
                    if (ossData.containsKey("accessKeySecret") && !((String) ossData.get("accessKeySecret")).isEmpty()) {
                        config.getOss().setAccessKeySecret((String) ossData.get("accessKeySecret"));
                    }
                }
            }
            
            // 更新Log配置
            if (body.containsKey("log")) {
                Map<String, Object> logData = (Map<String, Object>) body.get("log");
                if (config.getLog() != null) {
                    if (logData.containsKey("level")) {
                        config.getLog().setLevel((String) logData.get("level"));
                    }
                    if (logData.containsKey("retentionDays")) {
                        config.getLog().setMaxAge(((Number) logData.get("retentionDays")).intValue());
                    }
                }
            }
            
            // 更新Notification配置
            if (body.containsKey("notification")) {
                Map<String, Object> notifData = (Map<String, Object>) body.get("notification");
                Config.NotificationConfig notifConfig = config.getNotification();
                if (notifConfig == null) {
                    notifConfig = new Config.NotificationConfig();
                    config.setNotification(notifConfig);
                }
                
                if (notifData.containsKey("wechat")) {
                    Map<String, Object> wechatData = (Map<String, Object>) notifData.get("wechat");
                    Config.NotificationChannel wechat = notifConfig.getWechat();
                    if (wechat == null) {
                        wechat = new Config.NotificationChannel();
                        notifConfig.setWechat(wechat);
                    }
                    if (wechatData.containsKey("enabled")) wechat.setEnabled((Boolean) wechatData.get("enabled"));
                    if (wechatData.containsKey("webhookUrl")) wechat.setWebhookUrl((String) wechatData.get("webhookUrl"));
                }
                
                if (notifData.containsKey("dingtalk")) {
                    Map<String, Object> dingtalkData = (Map<String, Object>) notifData.get("dingtalk");
                    Config.NotificationChannel dingtalk = notifConfig.getDingtalk();
                    if (dingtalk == null) {
                        dingtalk = new Config.NotificationChannel();
                        notifConfig.setDingtalk(dingtalk);
                    }
                    if (dingtalkData.containsKey("enabled")) dingtalk.setEnabled((Boolean) dingtalkData.get("enabled"));
                    if (dingtalkData.containsKey("webhookUrl")) dingtalk.setWebhookUrl((String) dingtalkData.get("webhookUrl"));
                    if (dingtalkData.containsKey("secret")) dingtalk.setSecret((String) dingtalkData.get("secret"));
                }
                
                if (notifData.containsKey("feishu")) {
                    Map<String, Object> feishuData = (Map<String, Object>) notifData.get("feishu");
                    Config.NotificationChannel feishu = notifConfig.getFeishu();
                    if (feishu == null) {
                        feishu = new Config.NotificationChannel();
                        notifConfig.setFeishu(feishu);
                    }
                    if (feishuData.containsKey("enabled")) feishu.setEnabled((Boolean) feishuData.get("enabled"));
                    if (feishuData.containsKey("webhookUrl")) feishu.setWebhookUrl((String) feishuData.get("webhookUrl"));
                }
            }

            // 更新 AI 服务配置（空或脱敏值不覆盖原 apiKey/ttsApiKey）
            if (body.containsKey("ai")) {
                Map<String, Object> aiData = (Map<String, Object>) body.get("ai");
                Config.AiConfig ai = config.getAi();
                if (ai == null) {
                    ai = new Config.AiConfig();
                    config.setAi(ai);
                }
                if (aiData.containsKey("enabled")) {
                    ai.setEnabled(Boolean.TRUE.equals(aiData.get("enabled")));
                }
                if (aiData.containsKey("provider")) {
                    ai.setProvider((String) aiData.get("provider"));
                }
                if (aiData.containsKey("baseUrl")) {
                    ai.setBaseUrl((String) aiData.get("baseUrl"));
                }
                String apiKeyVal = (String) aiData.get("apiKey");
                if (apiKeyVal != null && !apiKeyVal.isEmpty() && !apiKeyVal.endsWith("****")) {
                    ai.setApiKey(apiKeyVal);
                }
                if (aiData.containsKey("defaultModel")) {
                    ai.setDefaultModel((String) aiData.get("defaultModel"));
                }
                if (aiData.containsKey("ttsProvider")) {
                    ai.setTtsProvider((String) aiData.get("ttsProvider"));
                }
                String ttsApiKeyVal = (String) aiData.get("ttsApiKey");
                if (ttsApiKeyVal != null && !ttsApiKeyVal.isEmpty() && !ttsApiKeyVal.endsWith("****")) {
                    ai.setTtsApiKey(ttsApiKeyVal);
                }
                if (aiData.containsKey("ttsGroupId")) {
                    ai.setTtsGroupId((String) aiData.get("ttsGroupId"));
                }
                if (aiData.containsKey("ttsModel")) {
                    ai.setTtsModel((String) aiData.get("ttsModel"));
                }
                if (aiData.containsKey("ttsVoice")) {
                    ai.setTtsVoice((String) aiData.get("ttsVoice"));
                }
            }
            
            // 保存配置
            configService.updateConfig(config);
            
            response.status(200);
            response.type("application/json");
            return createSuccessResponse(Map.of("message", "配置已更新"));
        } catch (Exception e) {
            logger.error("更新系统配置失败", e);
            response.status(500);
            return createErrorResponse(500, "更新系统配置失败: " + e.getMessage());
        }
    }

    private static String maskApiKey(String key) {
        if (key == null || key.isEmpty()) return "";
        if (key.length() <= 4) return "****";
        return key.substring(0, 4) + "****";
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

    /**
     * 系统健康检查
     * GET /api/system/health
     */
    public String healthCheck(Request request, Response response) {
        try {
            Map<String, Object> health = new HashMap<>();
            
            // 检查MQTT连接状态
            boolean mqttConnected = mqttClient != null && mqttClient.isConnected();
            Map<String, Object> mqttStatus = new HashMap<>();
            mqttStatus.put("connected", mqttConnected);
            health.put("mqtt", mqttStatus);
            
            // 检查数据库连接
            Map<String, Object> dbStatus = new HashMap<>();
            dbStatus.put("status", "ok");
            health.put("database", dbStatus);
            
            // 检查SDK状态
            Map<String, Object> sdkStatus = new HashMap<>();
            sdkStatus.put("status", "ok");
            health.put("sdk", sdkStatus);
            
            // 检查磁盘空间
            File rootDir = new File(".");
            long freeSpace = rootDir.getFreeSpace();
            long totalSpace = rootDir.getTotalSpace();
            double diskUsagePercent = totalSpace > 0 ? (1.0 - (double) freeSpace / totalSpace) * 100 : 0;
            Map<String, Object> diskInfo = new HashMap<>();
            diskInfo.put("freeSpace", formatBytes(freeSpace));
            diskInfo.put("totalSpace", formatBytes(totalSpace));
            diskInfo.put("usagePercent", String.format("%.1f%%", diskUsagePercent));
            health.put("disk", diskInfo);
            
            // 总体健康状态
            boolean isHealthy = mqttConnected && diskUsagePercent < 90;
            health.put("status", isHealthy ? "healthy" : "warning");
            
            response.status(200);
            response.type("application/json");
            return createSuccessResponse(health);
        } catch (Exception e) {
            logger.error("健康检查失败", e);
            response.status(500);
            return createErrorResponse(500, "健康检查失败: " + e.getMessage());
        }
    }

    /**
     * 重启MQTT连接
     * POST /api/system/mqtt/restart
     */
    public String restartMqtt(Request request, Response response) {
        try {
            if (mqttClient == null) {
                response.status(400);
                return createErrorResponse(400, "MQTT客户端未初始化");
            }
            
            // 断开连接
            mqttClient.disconnect();
            
            // 等待1秒后重连
            Thread.sleep(1000);
            
            // 重新连接
            boolean connected = mqttClient.connect();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", connected);
            result.put("message", connected ? "MQTT重启成功" : "MQTT重启失败");
            
            response.status(connected ? 200 : 500);
            response.type("application/json");
            return createSuccessResponse(result);
        } catch (Exception e) {
            logger.error("重启MQTT失败", e);
            response.status(500);
            return createErrorResponse(500, "重启MQTT失败: " + e.getMessage());
        }
    }

    /**
     * 测试通知发送
     * POST /api/system/notification/test
     */
    @SuppressWarnings("unchecked")
    public String testNotification(Request request, Response response) {
        try {
            Map<String, Object> body = objectMapper.readValue(request.body(), Map.class);
            
            String channel = (String) body.get("channel");
            String webhookUrl = (String) body.get("webhookUrl");
            String secret = (String) body.getOrDefault("secret", "");
            
            if (channel == null || webhookUrl == null || webhookUrl.isEmpty()) {
                response.status(400);
                return createErrorResponse(400, "渠道类型和Webhook URL不能为空");
            }
            
            // 创建临时的通知服务进行测试
            Config.NotificationConfig tempConfig = new Config.NotificationConfig();
            NotificationService tempService = new NotificationService(tempConfig);
            
            boolean success = tempService.testNotification(channel, webhookUrl, secret);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            result.put("message", success ? "测试消息发送成功" : "测试消息发送失败");
            
            response.status(success ? 200 : 500);
            response.type("application/json");
            return createSuccessResponse(result);
        } catch (Exception e) {
            logger.error("测试通知发送失败", e);
            response.status(500);
            return createErrorResponse(500, "测试通知发送失败: " + e.getMessage());
        }
    }

    private static final String TTS_TEST_URL = "https://api.minimaxi.com/v1/t2a_v2";

    /**
     * 测试 AI 连接：验证 AI 网关（chat）和/或 MiniMax TTS 是否可用。
     * POST /api/system/ai/test
     */
    @SuppressWarnings("unchecked")
    public String testAiConnection(Request request, Response response) {
        try {
            Config config = configService.getConfig();
            Config.AiConfig ai = config != null ? config.getAi() : null;
            if (ai == null) {
                response.status(400);
                return createErrorResponse(400, "未配置 AI 选项");
            }
            List<String> ok = new ArrayList<>();
            List<String> err = new ArrayList<>();
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

            // 1) 若启用了 AI 且配置了 baseUrl + apiKey，测试 chat/completions
            if (ai.isEnabled() && ai.getBaseUrl() != null && !ai.getBaseUrl().isBlank()
                    && ai.getApiKey() != null && !ai.getApiKey().isBlank()) {
                try {
                    String baseUrl = ai.getBaseUrl().endsWith("/") ? ai.getBaseUrl() : ai.getBaseUrl() + "/";
                    String url = baseUrl + "chat/completions";
                    String model = (ai.getDefaultModel() != null && !ai.getDefaultModel().isBlank())
                            ? ai.getDefaultModel() : "google/gemini-2.0-flash-001";
                    Map<String, Object> body = new HashMap<>();
                    body.put("model", model);
                    body.put("max_tokens", 10);
                    body.put("messages", List.of(Map.of("role", "user", "content", "测试")));
                    String bodyJson = objectMapper.writeValueAsString(body);
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + ai.getApiKey())
                            .timeout(Duration.ofSeconds(30))
                            .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                            .build();
                    HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
                    if (res.statusCode() >= 200 && res.statusCode() < 300) {
                        ok.add("AI 网关连接成功");
                    } else {
                        err.add("AI 网关: HTTP " + res.statusCode() + " " + (res.body() != null && res.body().length() > 200 ? res.body().substring(0, 200) + "..." : res.body()));
                    }
                } catch (Exception e) {
                    err.add("AI 网关: " + e.getMessage());
                }
            }

            // 2) 若配置了 TTS API Key，测试 MiniMax TTS
            if (ai.getTtsApiKey() != null && !ai.getTtsApiKey().isBlank()) {
                try {
                    String model = (ai.getTtsModel() != null && !ai.getTtsModel().isBlank()) ? ai.getTtsModel() : "speech-02-hd";
                    String voice = (ai.getTtsVoice() != null && !ai.getTtsVoice().isBlank()) ? ai.getTtsVoice() : "male-qn-qingse";
                    Map<String, Object> body = new HashMap<>();
                    body.put("model", model);
                    body.put("text", "测试");
                    body.put("stream", false);
                    body.put("voice_setting", Map.of("voice_id", voice, "speed", 1, "vol", 1, "pitch", 0));
                    body.put("audio_setting", Map.of("sample_rate", 32000, "bitrate", 128000, "format", "mp3", "channel", 1));
                    String bodyJson = objectMapper.writeValueAsString(body);
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(TTS_TEST_URL))
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + ai.getTtsApiKey())
                            .timeout(Duration.ofSeconds(30))
                            .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                            .build();
                    HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
                    if (res.statusCode() == 200) {
                        Map<String, Object> root = objectMapper.readValue(res.body(), Map.class);
                        Map<String, Object> data = (Map<String, Object>) root.get("data");
                        if (data != null && data.get("audio") != null) {
                            ok.add("TTS 连接成功");
                        } else {
                            err.add("TTS 响应格式异常");
                        }
                    } else {
                        err.add("TTS: HTTP " + res.statusCode() + " " + (res.body() != null && res.body().length() > 200 ? res.body().substring(0, 200) + "..." : res.body()));
                    }
                } catch (Exception e) {
                    err.add("TTS: " + e.getMessage());
                }
            }

            if (ok.isEmpty() && err.isEmpty()) {
                response.status(400);
                return createErrorResponse(400, "请先配置 AI 网关（baseUrl + apiKey）或 TTS API Key");
            }
            if (!err.isEmpty()) {
                response.status(500);
                return createErrorResponse(500, String.join("；", err));
            }
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", String.join("；", ok));
            response.status(200);
            response.type("application/json");
            return createSuccessResponse(result);
        } catch (Exception e) {
            logger.error("测试 AI 连接失败", e);
            response.status(500);
            return createErrorResponse(500, "测试失败: " + e.getMessage());
        }
    }

    /**
     * 获取日志内容
     * GET /api/system/logs
     */
    public String getLogs(Request request, Response response) {
        try {
            String logFilePath = "./logs/app.log";
            Config config = configService.getConfig();
            if (config.getLog() != null && config.getLog().getFile() != null) {
                logFilePath = config.getLog().getFile();
            }
            
            File logFile = new File(logFilePath);
            if (!logFile.exists()) {
                response.status(404);
                return createErrorResponse(404, "日志文件不存在");
            }
            
            // 读取最后N行日志（默认100行）
            int lines = 100;
            String linesParam = request.queryParams("lines");
            if (linesParam != null) {
                try {
                    lines = Integer.parseInt(linesParam);
                    if (lines > 1000) lines = 1000; // 限制最大行数
                } catch (NumberFormatException e) {
                    // 使用默认值
                }
            }
            
            List<String> logLines = readLastLines(logFile, lines);
            
            Map<String, Object> result = new HashMap<>();
            result.put("file", logFilePath);
            result.put("lines", logLines.size());
            result.put("content", logLines);
            
            response.status(200);
            response.type("application/json");
            return createSuccessResponse(result);
        } catch (Exception e) {
            logger.error("获取日志失败", e);
            response.status(500);
            return createErrorResponse(500, "获取日志失败: " + e.getMessage());
        }
    }

    /**
     * 读取文件的最后N行
     */
    private List<String> readLastLines(File file, int n) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
                if (lines.size() > n) {
                    lines.remove(0);
                }
            }
        }
        return lines;
    }

    /**
     * 格式化字节数
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else if (bytes < 1024L * 1024 * 1024 * 1024) {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        } else {
            return String.format("%.2f TB", bytes / (1024.0 * 1024.0 * 1024.0 * 1024.0));
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

    private void resolveUrl(java.util.Map<String, Object> record, String field, String baseUrl) {
        Object val = record.get(field);
        if (val instanceof String) {
            String s = (String) val;
            if (s.startsWith("/")) {
                record.put(field, baseUrl + s);
            }
        }
    }

    /**
     * 获取 AI 分析记录列表
     * GET /api/system/ai-analysis-records?limit=100&offset=0
     */
    public String getAiAnalysisRecords(Request request, Response response) {
        try {
            if (aiAnalysisService == null) {
                response.status(200);
                return createSuccessResponse(java.util.Collections.emptyList());
            }
            int limit = 100;
            int offset = 0;
            try {
                String l = request.queryParams("limit");
                if (l != null) limit = Integer.parseInt(l);
                String o = request.queryParams("offset");
                if (o != null) offset = Integer.parseInt(o);
            } catch (NumberFormatException ignored) {}

            java.util.List<java.util.Map<String, Object>> records = aiAnalysisService.getRecords(limit, offset);

            // 将相对路径补全为完整 URL（voiceUrl、imageUrl）
            String baseUrl = request.scheme() + "://" + request.host();
            for (java.util.Map<String, Object> rec : records) {
                resolveUrl(rec, "voiceUrl", baseUrl);
                resolveUrl(rec, "imageUrl", baseUrl);
            }

            response.status(200);
            return createSuccessResponse(records);
        } catch (Exception e) {
            logger.error("获取AI分析记录失败", e);
            response.status(500);
            return createErrorResponse(500, "获取AI分析记录失败: " + e.getMessage());
        }
    }
}
