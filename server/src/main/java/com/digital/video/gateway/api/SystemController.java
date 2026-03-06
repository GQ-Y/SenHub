package com.digital.video.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.digital.video.gateway.config.Config;
import com.digital.video.gateway.mqtt.MqttClient;
import com.digital.video.gateway.service.ConfigService;
import com.digital.video.gateway.service.NotificationService;
import com.digital.video.gateway.Main;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private com.digital.video.gateway.database.Database database;

    /** 读取打包进 jar 的版本号（Maven shade 插件写入 MANIFEST.MF） */
    public static String readAppVersion() {
        try {
            java.util.jar.Manifest mf = new java.util.jar.Manifest(
                Main.class.getResourceAsStream("/META-INF/MANIFEST.MF"));
            String v = mf.getMainAttributes().getValue("Implementation-Version");
            if (v != null && !v.isBlank()) return v.trim();
        } catch (Exception ignored) {}
        // fallback: 从 pom.properties
        try (java.io.InputStream is = Main.class.getResourceAsStream(
                "/META-INF/maven/com.digital.video.gateway/senhub-app/pom.properties")) {
            if (is != null) {
                java.util.Properties p = new java.util.Properties();
                p.load(is);
                String v = p.getProperty("version");
                if (v != null && !v.isBlank()) return v.trim();
            }
        } catch (Exception ignored) {}
        return "unknown";
    }

    public void setAiAnalysisService(com.digital.video.gateway.service.AiAnalysisService aiAnalysisService) {
        this.aiAnalysisService = aiAnalysisService;
    }

    public void setDatabase(com.digital.video.gateway.database.Database database) {
        this.database = database;
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
    public void getConfig(Context ctx) {
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
            
            ctx.status(200);
            ctx.contentType("application/json");
            ctx.result(createSuccessResponse(systemConfig));
        } catch (Exception e) {
            logger.error("获取系统配置失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "获取系统配置失败: " + e.getMessage()));
        }
    }

    /**
     * 更新系统配置
     * PUT /api/system/config
     */
    @SuppressWarnings("unchecked")
    public void updateConfig(Context ctx) {
        try {
            Map<String, Object> body = objectMapper.readValue(ctx.body(), Map.class);
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
            
            ctx.status(200);
            ctx.contentType("application/json");
            ctx.result(createSuccessResponse(Map.of("message", "配置已更新")));
        } catch (Exception e) {
            logger.error("更新系统配置失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "更新系统配置失败: " + e.getMessage()));
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
    public void healthCheck(Context ctx) {
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
            
            ctx.status(200);
            ctx.contentType("application/json");
            ctx.result(createSuccessResponse(health));
        } catch (Exception e) {
            logger.error("健康检查失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "健康检查失败: " + e.getMessage()));
        }
    }

    /**
     * 重启MQTT连接
     * POST /api/system/mqtt/restart
     */
    public void restartMqtt(Context ctx) {
        try {
            if (mqttClient == null) {
                ctx.status(400);
                ctx.result(createErrorResponse(400, "MQTT客户端未初始化"));
                return;
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
            
            ctx.status(connected ? 200 : 500);
            ctx.contentType("application/json");
            ctx.result(createSuccessResponse(result));
        } catch (Exception e) {
            logger.error("重启MQTT失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "重启MQTT失败: " + e.getMessage()));
        }
    }

    /**
     * 测试通知发送
     * POST /api/system/notification/test
     */
    @SuppressWarnings("unchecked")
    public void testNotification(Context ctx) {
        try {
            Map<String, Object> body = objectMapper.readValue(ctx.body(), Map.class);
            
            String channel = (String) body.get("channel");
            String webhookUrl = (String) body.get("webhookUrl");
            String secret = (String) body.getOrDefault("secret", "");
            
            if (channel == null || webhookUrl == null || webhookUrl.isEmpty()) {
                ctx.status(400);
                ctx.result(createErrorResponse(400, "渠道类型和Webhook URL不能为空"));
                return;
            }
            // 创建临时的通知服务进行测试
            Config.NotificationConfig tempConfig = new Config.NotificationConfig();
            NotificationService tempService = new NotificationService(tempConfig);
            
            boolean success = tempService.testNotification(channel, webhookUrl, secret);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            result.put("message", success ? "测试消息发送成功" : "测试消息发送失败");
            
            ctx.status(success ? 200 : 500);
            ctx.contentType("application/json");
            ctx.result(createSuccessResponse(result));
        } catch (Exception e) {
            logger.error("测试通知发送失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "测试通知发送失败: " + e.getMessage()));
        }
    }

    private static final String TTS_TEST_URL = "https://api.minimaxi.com/v1/t2a_v2";

    /**
     * 测试 AI 连接：验证 AI 网关（chat）和/或 MiniMax TTS 是否可用。
     * POST /api/system/ai/test
     */
    @SuppressWarnings("unchecked")
    public void testAiConnection(Context ctx) {
        try {
            Config config = configService.getConfig();
            Config.AiConfig ai = config != null ? config.getAi() : null;
            if (ai == null) {
                ctx.status(400);
                ctx.result(createErrorResponse(400, "未配置 AI 选项"));
                return;
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
                ctx.status(400);
                ctx.result(createErrorResponse(400, "请先配置 AI 网关（baseUrl + apiKey）或 TTS API Key"));
                return;
            }
            if (!err.isEmpty()) {
                ctx.status(500);
                ctx.result(createErrorResponse(500, String.join("；", err)));
            }
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", String.join("；", ok));
            ctx.status(200);
            ctx.contentType("application/json");
            ctx.result(createSuccessResponse(result));
        } catch (Exception e) {
            logger.error("测试 AI 连接失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "测试失败: " + e.getMessage()));
        }
    }

    /**
     * 获取日志内容
     * GET /api/system/logs
     */
    public void getLogs(Context ctx) {
        try {
            String logFilePath = "./logs/app.log";
            Config config = configService.getConfig();
            if (config.getLog() != null && config.getLog().getFile() != null) {
                logFilePath = config.getLog().getFile();
            }
            
            File logFile = new File(logFilePath);
            if (!logFile.exists()) {
                ctx.status(404);
                ctx.result(createErrorResponse(404, "日志文件不存在"));
                return;
            }
            
            // 读取最后N行日志（默认100行）
            int lines = 100;
            String linesParam = ctx.queryParam("lines");
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
            
            ctx.status(200);
            ctx.contentType("application/json");
            ctx.result(createSuccessResponse(result));
        } catch (Exception e) {
            logger.error("获取日志失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "获取日志失败: " + e.getMessage()));
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
     * 获取 AI 分析记录列表（分页 + 事件类型 + 时间范围筛选）
     * GET /api/system/ai-analysis-records?limit=100&offset=0&eventType=LOITERING&startTime=2026-02-01&endTime=2026-02-28
     */
    public void getAiAnalysisRecords(Context ctx) {
        try {
            if (aiAnalysisService == null) {
                ctx.status(200);
                ctx.result(createSuccessResponse(Map.of("data", java.util.Collections.emptyList(), "total", 0)));
                return;
            }
            int limit = 100;
            int offset = 0;
            try {
                String l = ctx.queryParam("limit");
                if (l != null) limit = Integer.parseInt(l);
                String o = ctx.queryParam("offset");
                if (o != null) offset = Integer.parseInt(o);
            } catch (NumberFormatException ignored) {}
            String eventType = ctx.queryParam("eventType");
            String startTime = ctx.queryParam("startTime");
            String endTime = ctx.queryParam("endTime");

            java.util.List<java.util.Map<String, Object>> records = aiAnalysisService.getRecords(limit, offset, eventType, startTime, endTime);
            int total = aiAnalysisService.countRecords(eventType, startTime, endTime);

            String baseUrl = ctx.scheme() + "://" + ctx.host();
            for (java.util.Map<String, Object> rec : records) {
                resolveUrl(rec, "voiceUrl", baseUrl);
                resolveUrl(rec, "imageUrl", baseUrl);
            }

            Map<String, Object> body = new HashMap<>();
            body.put("data", records);
            body.put("total", total);
            ctx.status(200);
            ctx.result(createSuccessResponse(body));
        } catch (Exception e) {
            logger.error("获取AI分析记录失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "获取AI分析记录失败: " + e.getMessage()));
        }
    }

    /**
     * 删除单条 AI 分析记录
     * DELETE /api/system/ai-analysis-records/:id
     */
    public void deleteAiAnalysisRecord(Context ctx) {
        try {
            if (aiAnalysisService == null) {
                ctx.status(404);
                ctx.result(createErrorResponse(404, "服务不可用"));
                return;
            }
            String id = ctx.pathParam("id");
            boolean deleted = aiAnalysisService.deleteRecord(id);
            if (!deleted) {
                ctx.status(404);
                ctx.result(createErrorResponse(404, "记录不存在或已删除"));
                return;
            }
            ctx.status(200);
            ctx.result(createSuccessResponse(Map.of("message", "删除成功")));
        } catch (Exception e) {
            logger.error("删除AI分析记录失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "删除失败: " + e.getMessage()));
        }
    }

    /**
     * 批量删除 AI 分析记录
     * POST /api/system/ai-analysis-records/batch-delete
     */
    @SuppressWarnings("unchecked")
    public void batchDeleteAiAnalysisRecords(Context ctx) {
        try {
            if (aiAnalysisService == null) {
                ctx.status(404);
                ctx.result(createErrorResponse(404, "服务不可用"));
                return;
            }
            Map<String, Object> body = objectMapper.readValue(ctx.body(), Map.class);
            Object idsObj = body.get("ids");
            java.util.List<String> ids = new ArrayList<>();
            if (idsObj instanceof java.util.List) {
                for (Object o : (java.util.List<?>) idsObj) {
                    if (o != null) ids.add(o.toString());
                }
            }
            int deleted = aiAnalysisService.deleteRecords(ids);
            Map<String, Object> result = new HashMap<>();
            result.put("deleted", deleted);
            result.put("message", "成功删除 " + deleted + " 条");
            ctx.status(200);
            ctx.result(createSuccessResponse(result));
        } catch (Exception e) {
            logger.error("批量删除AI分析记录失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "批量删除失败: " + e.getMessage()));
        }
    }

    /**
     * 获取系统基础信息（版本号、系统名称）
     * GET /api/system/info
     */
    public void getSystemInfo(Context ctx) {
        try {
            Map<String, Object> info = new HashMap<>();
            info.put("version", readAppVersion());
            // 系统名称从 configs 表读取，fallback 到默认
            String systemName = database != null ? database.getConfig("system.name") : null;
            if (systemName == null || systemName.isBlank()) systemName = "SenHub";
            info.put("systemName", systemName);
            // 自动更新配置
            if (database != null) {
                info.put("autoUpdate", buildAutoUpdateInfo());
            }
            ctx.status(200);
            ctx.contentType("application/json");
            ctx.result(createSuccessResponse(info));
        } catch (Exception e) {
            logger.error("获取系统信息失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, e.getMessage()));
        }
    }

    /**
     * 更新系统基本信息（系统名称等）
     * PUT /api/system/info
     */
    @SuppressWarnings("unchecked")
    public void updateSystemInfo(Context ctx) {
        try {
            Map<String, Object> body = objectMapper.readValue(ctx.body(), Map.class);
            if (database == null) {
                ctx.status(503);
                ctx.result(createErrorResponse(503, "数据库不可用"));
                return;
            }
            Object name = body.get("systemName");
            if (name != null && !name.toString().isBlank()) {
                database.saveOrUpdateConfig("system.name", name.toString().trim(), "string");
            }
            ctx.status(200);
            ctx.result(createSuccessResponse(Map.of("message", "已保存")));
        } catch (Exception e) {
            logger.error("更新系统信息失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, e.getMessage()));
        }
    }

    // ==================== 自动更新 ====================

    private static final String UPDATE_KEY_ENABLED      = "auto_update.enabled";
    private static final String UPDATE_KEY_SCHEDULE     = "auto_update.schedule";   // e.g. FRIDAY,02:00
    private static final String UPDATE_KEY_UPDATE_URL   = "auto_update.update_url";
    private static final String DEFAULT_UPDATE_URL      = "http://demo.zt.admins.smartrail.cloud";

    private Map<String, Object> buildAutoUpdateInfo() {
        if (database == null) return Map.of();
        String enabled   = database.getConfig(UPDATE_KEY_ENABLED);
        String schedule  = database.getConfig(UPDATE_KEY_SCHEDULE);
        String updateUrl = database.getConfig(UPDATE_KEY_UPDATE_URL);
        Map<String, Object> m = new HashMap<>();
        m.put("enabled",   "true".equalsIgnoreCase(enabled));
        m.put("schedule",  schedule  != null ? schedule  : "FRIDAY,02:00");
        m.put("updateUrl", updateUrl != null ? updateUrl : DEFAULT_UPDATE_URL);
        return m;
    }

    /**
     * 获取自动更新配置
     * GET /api/system/auto-update
     */
    public void getAutoUpdate(Context ctx) {
        try {
            ctx.status(200);
            ctx.contentType("application/json");
            ctx.result(createSuccessResponse(buildAutoUpdateInfo()));
        } catch (Exception e) {
            ctx.status(500);
            ctx.result(createErrorResponse(500, e.getMessage()));
        }
    }

    /**
     * 保存自动更新配置
     * PUT /api/system/auto-update
     * body: { "enabled": true, "schedule": "FRIDAY,02:00", "updateUrl": "http://..." }
     */
    @SuppressWarnings("unchecked")
    public void saveAutoUpdate(Context ctx) {
        try {
            if (database == null) {
                ctx.status(503);
                ctx.result(createErrorResponse(503, "数据库不可用"));
                return;
            }
            Map<String, Object> body = objectMapper.readValue(ctx.body(), Map.class);
            if (body.containsKey("enabled"))   database.saveOrUpdateConfig(UPDATE_KEY_ENABLED,   String.valueOf(body.get("enabled")), "boolean");
            if (body.containsKey("schedule"))  database.saveOrUpdateConfig(UPDATE_KEY_SCHEDULE,  body.get("schedule").toString(), "string");
            if (body.containsKey("updateUrl")) database.saveOrUpdateConfig(UPDATE_KEY_UPDATE_URL, body.get("updateUrl").toString(), "string");
            ctx.status(200);
            ctx.result(createSuccessResponse(buildAutoUpdateInfo()));
        } catch (Exception e) {
            logger.error("保存自动更新配置失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, e.getMessage()));
        }
    }

    /**
     * 检查远程是否有新版本
     * GET /api/system/auto-update/check
     * 返回: { currentVersion, latestVersion, hasUpdate }
     */
    public void checkForUpdate(Context ctx) {
        try {
            String updateUrl = database != null ? database.getConfig(UPDATE_KEY_UPDATE_URL) : null;
            if (updateUrl == null || updateUrl.isBlank()) updateUrl = DEFAULT_UPDATE_URL;
            String current = readAppVersion();
            String latest = fetchLatestVersion(updateUrl);
            Map<String, Object> result = new HashMap<>();
            result.put("currentVersion", current);
            result.put("latestVersion",  latest != null ? latest : current);
            result.put("hasUpdate", latest != null && !latest.equals(current));
            result.put("updateUrl", updateUrl);
            ctx.status(200);
            ctx.contentType("application/json");
            ctx.result(createSuccessResponse(result));
        } catch (Exception e) {
            logger.error("检查更新失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, e.getMessage()));
        }
    }

    /**
     * 立即执行更新（异步，调用 vgw update）
     * POST /api/system/auto-update/apply
     */
    public void applyUpdate(Context ctx) {
        try {
            String updateUrl = database != null ? database.getConfig(UPDATE_KEY_UPDATE_URL) : null;
            if (updateUrl == null || updateUrl.isBlank()) updateUrl = DEFAULT_UPDATE_URL;
            final String finalUpdateUrl = updateUrl;
            // 先响应客户端，再异步执行更新
            Map<String, Object> resp = new HashMap<>();
            resp.put("message", "更新任务已启动，系统将在更新完成后自动重启");
            ctx.status(200);
            ctx.contentType("application/json");
            ctx.result(createSuccessResponse(resp));
            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                    logger.info("开始执行自动更新, updateUrl={}", finalUpdateUrl);
                    // 设置更新地址环境变量后调用 vgw update
                    ProcessBuilder pb = new ProcessBuilder("bash", "-c",
                            "UPDATE_SERVER_URL=\"" + finalUpdateUrl + "\" /usr/local/bin/vgw update >> /opt/senhub/logs/update.log 2>&1");
                    pb.redirectErrorStream(true);
                    pb.start();
                } catch (Exception e) {
                    logger.error("执行更新失败", e);
                }
            }, "auto-update").start();
        } catch (Exception e) {
            logger.error("触发更新失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, e.getMessage()));
        }
    }

    private String fetchLatestVersion(String baseUrl) {
        try {
            String url = baseUrl.replaceAll("/$", "") + "/LATEST_VERSION";
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) return resp.body().trim();
        } catch (Exception e) {
            logger.warn("获取最新版本号失败: {}", e.getMessage());
        }
        return null;
    }

}

