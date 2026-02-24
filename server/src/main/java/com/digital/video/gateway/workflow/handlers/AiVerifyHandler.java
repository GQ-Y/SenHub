package com.digital.video.gateway.workflow.handlers;

import com.digital.video.gateway.service.AiAnalysisService;
import com.digital.video.gateway.service.AiGatewayClient;
import com.digital.video.gateway.workflow.FlowContext;
import com.digital.video.gateway.workflow.FlowNodeDefinition;
import com.digital.video.gateway.workflow.FlowNodeHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * AI 报警图片核验节点：使用多模态 chat completion 判断图片内容是否与报警事件一致。
 * 通过 prompt 约束输出 JSON 格式（不依赖 function call），兼容更多模型。
 */
public class AiVerifyHandler implements FlowNodeHandler {
    private static final Logger logger = LoggerFactory.getLogger(AiVerifyHandler.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final int MAX_RETRIES = 3;
    private static final String SKIP_REASON = "核验超时跳过";

    private final AiGatewayClient aiClient;
    private final AiAnalysisService aiAnalysisService;

    public AiVerifyHandler(AiGatewayClient aiClient) {
        this.aiClient = aiClient;
        this.aiAnalysisService = null;
    }

    public AiVerifyHandler(AiGatewayClient aiClient, AiAnalysisService aiAnalysisService) {
        this.aiClient = aiClient;
        this.aiAnalysisService = aiAnalysisService;
    }

    @Override
    public boolean execute(FlowNodeDefinition node, FlowContext context) throws Exception {
        if (aiClient == null) {
            logger.warn("AiGatewayClient 未初始化，跳过 ai_verify");
            context.putVariable("_last_node_branch", "true");
            context.putVariable("ai_verify_match", true);
            context.putVariable("ai_verify_reason", SKIP_REASON);
            return true;
        }

        String imageUrl = null;
        String base64Data = null;
        String mimeType = "image/jpeg";

        if (context.getVariables() != null) {
            Object v = context.getVariables().get("captureOssUrl");
            if (v instanceof String && !((String) v).isBlank()) {
                imageUrl = (String) v;
            }
            if (imageUrl == null) {
                v = context.getVariables().get("captureUrl");
                if (v instanceof String && !((String) v).isBlank()) {
                    imageUrl = (String) v;
                }
            }
        }

        if (imageUrl == null && context.getVariables() != null) {
            Object pathObj = context.getVariables().get("capturePath");
            if (pathObj instanceof String) {
                String path = (String) pathObj;
                try {
                    byte[] bytes = Files.readAllBytes(java.nio.file.Paths.get(path));
                    base64Data = Base64.getEncoder().encodeToString(bytes);
                    String lower = path.toLowerCase();
                    if (lower.endsWith(".png")) mimeType = "image/png";
                    else if (lower.endsWith(".gif")) mimeType = "image/gif";
                    else if (lower.endsWith(".webp")) mimeType = "image/webp";
                } catch (Exception e) {
                    logger.warn("读取抓图文件失败: {}", path, e);
                }
            }
        }

        if (imageUrl == null && base64Data == null) {
            logger.warn("ai_verify 无可用图片（captureOssUrl/captureUrl/capturePath），跳过核验");
            context.putVariable("_last_node_branch", "true");
            context.putVariable("ai_verify_match", true);
            context.putVariable("ai_verify_reason", "无图片跳过");
            return true;
        }

        String alarmType = context.getAlarmType() != null ? context.getAlarmType() : "未知";
        String userText = "当前报警事件类型为：" + alarmType + "。请根据图片内容判断：图片中的场景是否与该报警事件一致。\n"
                + "你必须严格按照以下JSON格式回复，不要包含其他文字：\n"
                + "{\"match\": true或false, \"reason\": \"判定理由\"}\n"
                + "其中 match 为 true 表示图片内容与报警事件一致，false 表示不一致。";

        Map<String, Object> cfg = node.getConfig();
        if (cfg != null && cfg.get("prompt") instanceof String) {
            String extra = ((String) cfg.get("prompt")).trim();
            if (!extra.isEmpty()) {
                userText = userText + "\n附加说明：" + extra;
            }
        }

        String model = null;
        if (cfg != null && cfg.get("model") instanceof String) {
            model = ((String) cfg.get("model")).trim();
        }
        if (model == null || model.isEmpty()) {
            model = null;
        }

        String imgDataUrl = imageUrl != null ? imageUrl : ("data:" + mimeType + ";base64," + base64Data);

        boolean match = true;
        String reason = SKIP_REASON;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                String content = aiClient.chatCompletionWithImage(userText, imgDataUrl, model);
                VerifyResult result = parseJsonResult(content);
                if (result != null) {
                    match = result.match;
                    reason = result.reason != null ? result.reason : "";
                    logger.info("ai_verify 核验结果: match={}, reason={}", match, reason);
                    break;
                } else {
                    logger.warn("ai_verify 第 {} 次返回内容无法解析为JSON: {}", attempt,
                            content != null && content.length() > 200 ? content.substring(0, 200) + "..." : content);
                }
            } catch (Exception e) {
                logger.warn("ai_verify 第 {} 次请求失败: {}", attempt, e.getMessage());
            }
            if (attempt < MAX_RETRIES) {
                try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
        }

        context.putVariable("_last_node_branch", match ? "true" : "false");
        context.putVariable("ai_verify_match", match);
        context.putVariable("ai_verify_reason", reason);

        if (aiAnalysisService != null) {
            try {
                String recordImageUrl = null;

                // 优先使用 OSS URL
                if (context.getVariables() != null) {
                    Object ossUrl = context.getVariables().get("captureOssUrl");
                    if (ossUrl instanceof String && !((String) ossUrl).isBlank()) {
                        recordImageUrl = (String) ossUrl;
                    }
                    if (recordImageUrl == null) {
                        ossUrl = context.getVariables().get("ossUrl");
                        if (ossUrl instanceof String && !((String) ossUrl).isBlank()) {
                            recordImageUrl = (String) ossUrl;
                        }
                    }
                }

                // 并行分支可能没有 OSS URL，从本地抓图文件生成静态文件 URL
                if (recordImageUrl == null && context.getVariables() != null) {
                    Object pathObj = context.getVariables().get("capturePath");
                    if (pathObj instanceof String) {
                        try {
                            Path srcFile = java.nio.file.Paths.get((String) pathObj);
                            if (Files.exists(srcFile)) {
                                Path captureDir = Path.of("data", "captures");
                                Files.createDirectories(captureDir);
                                String ext = ".jpg";
                                String srcName = srcFile.getFileName().toString().toLowerCase();
                                if (srcName.endsWith(".png")) ext = ".png";
                                else if (srcName.endsWith(".gif")) ext = ".gif";
                                else if (srcName.endsWith(".webp")) ext = ".webp";
                                String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
                                String destName = "capture_" + ts + ext;
                                Path dest = captureDir.resolve(destName);
                                Files.copy(srcFile, dest, StandardCopyOption.REPLACE_EXISTING);
                                recordImageUrl = "/api/static/captures/" + destName;
                                logger.info("抓图已复制到静态目录: {}", recordImageUrl);
                            }
                        } catch (Exception e) {
                            logger.warn("复制抓图到静态目录失败: {}", e.getMessage());
                        }
                    }
                }

                String eventTitle = context.getAlarmType() != null ? context.getAlarmType() : "";
                String eventName = "";
                if (context.getVariables() != null) {
                    Object zh = context.getVariables().get("eventNameZh");
                    if (zh instanceof String) eventName = (String) zh;
                }
                String recordId = aiAnalysisService.createRecord(recordImageUrl, eventTitle, eventName, match, reason);
                context.putVariable("_ai_analysis_record_id", recordId);
            } catch (Exception e) {
                logger.warn("创建AI分析记录失败: {}", e.getMessage());
            }
        }

        return true;
    }

    /**
     * 从模型回复中提取 JSON 结果（兼容带 markdown code block 的输出）
     */
    private VerifyResult parseJsonResult(String content) {
        if (content == null || content.isBlank()) return null;
        String json = content.trim();
        // 去掉 markdown code block: ```json ... ``` 或 ``` ... ```
        if (json.startsWith("```")) {
            int start = json.indexOf('\n');
            int end = json.lastIndexOf("```");
            if (start > 0 && end > start) {
                json = json.substring(start + 1, end).trim();
            }
        }
        // 尝试提取 JSON 对象
        int braceStart = json.indexOf('{');
        int braceEnd = json.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            json = json.substring(braceStart, braceEnd + 1);
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = mapper.readValue(json, Map.class);
            Object matchObj = parsed.get("match");
            boolean m;
            if (matchObj instanceof Boolean) {
                m = (Boolean) matchObj;
            } else {
                m = Boolean.parseBoolean(String.valueOf(matchObj));
            }
            String r = parsed.get("reason") != null ? parsed.get("reason").toString() : "";
            return new VerifyResult(m, r);
        } catch (Exception e) {
            logger.debug("JSON解析失败: {}", e.getMessage());
            return null;
        }
    }

    private static class VerifyResult {
        final boolean match;
        final String reason;
        VerifyResult(boolean match, String reason) {
            this.match = match;
            this.reason = reason;
        }
    }
}
