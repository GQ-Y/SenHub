package com.digital.video.gateway.workflow.handlers;

import com.digital.video.gateway.service.AiAnalysisService;
import com.digital.video.gateway.service.AiGatewayClient;
import com.digital.video.gateway.workflow.FlowContext;
import com.digital.video.gateway.workflow.FlowNodeDefinition;
import com.digital.video.gateway.workflow.FlowNodeHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Iterator;
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
    private static final long MAX_IMAGE_BYTES = 7 * 1024 * 1024; // base64 后约 9.3MB，留余量不超过 10MB 限制

    private final AiGatewayClient aiClient;
    private final AiAnalysisService aiAnalysisService;
    private final com.digital.video.gateway.database.Database database;

    public AiVerifyHandler(AiGatewayClient aiClient) {
        this.aiClient = aiClient;
        this.aiAnalysisService = null;
        this.database = null;
    }

    public AiVerifyHandler(AiGatewayClient aiClient, AiAnalysisService aiAnalysisService) {
        this.aiClient = aiClient;
        this.aiAnalysisService = aiAnalysisService;
        this.database = null;
    }

    public AiVerifyHandler(AiGatewayClient aiClient, AiAnalysisService aiAnalysisService, com.digital.video.gateway.database.Database database) {
        this.aiClient = aiClient;
        this.aiAnalysisService = aiAnalysisService;
        this.database = database;
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
                    String lower = path.toLowerCase();
                    if (lower.endsWith(".png")) mimeType = "image/png";
                    else if (lower.endsWith(".gif")) mimeType = "image/gif";
                    else if (lower.endsWith(".webp")) mimeType = "image/webp";

                    if (bytes.length > MAX_IMAGE_BYTES) {
                        logger.info("抓图文件过大({}字节)，压缩为JPEG: {}", bytes.length, path);
                        byte[] compressed = compressImageToJpeg(bytes, MAX_IMAGE_BYTES);
                        if (compressed != null) {
                            bytes = compressed;
                            mimeType = "image/jpeg";
                            logger.info("压缩后大小: {}字节", bytes.length);
                        } else {
                            logger.warn("图片压缩失败，使用原始文件");
                        }
                    }
                    base64Data = Base64.getEncoder().encodeToString(bytes);
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
        String defaultText = "当前报警事件类型为：" + alarmType + "。请根据图片内容判断：图片中的场景是否与该报警事件一致。\n"
                + "你必须严格按照以下JSON格式回复，不要包含其他文字：\n"
                + "{\"match\": true或false, \"reason\": \"判定理由\"}\n"
                + "其中 match 为 true 表示图片内容与报警事件一致，false 表示不一致。";

        // 提示词优先级：节点独立配置 > 事件配置 > 默认
        Map<String, Object> cfg = node.getConfig();
        String nodePrompt = null;
        if (cfg != null && cfg.get("prompt") instanceof String) {
            String p = ((String) cfg.get("prompt")).trim();
            if (!p.isEmpty()) nodePrompt = p;
        }

        String eventPrompt = null;
        if (database != null && context.getVariables() != null) {
            Object ek = context.getVariables().get("eventKey");
            if (ek instanceof String && !((String) ek).isEmpty()) {
                try (java.sql.Connection conn = database.getConnection()) {
                    eventPrompt = com.digital.video.gateway.database.CanonicalEventTable.getAiVerifyPrompt(conn, (String) ek);
                } catch (Exception e) {
                    logger.debug("读取事件 ai_verify_prompt 失败: {}", e.getMessage());
                }
            }
        }

        String userText;
        if (nodePrompt != null) {
            userText = defaultText + "\n附加说明：" + nodePrompt;
        } else if (eventPrompt != null && !eventPrompt.isBlank()) {
            userText = defaultText + "\n附加说明：" + eventPrompt;
        } else {
            userText = defaultText;
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

    /**
     * 将过大的图片压缩为 JPEG，逐步降低质量直到满足大小限制。
     * 如果图片分辨率过高（>4K），先缩小分辨率再压缩。
     */
    private byte[] compressImageToJpeg(byte[] originalBytes, long maxBytes) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(originalBytes));
            if (img == null) return null;

            int w = img.getWidth();
            int h = img.getHeight();
            // 超过 4K 分辨率时先缩小
            int maxDim = 3840;
            if (w > maxDim || h > maxDim) {
                double scale = Math.min((double) maxDim / w, (double) maxDim / h);
                int nw = (int) (w * scale);
                int nh = (int) (h * scale);
                BufferedImage scaled = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = scaled.createGraphics();
                g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(img, 0, 0, nw, nh, null);
                g.dispose();
                img = scaled;
                logger.info("图片缩放: {}x{} -> {}x{}", w, h, nw, nh);
            }

            // 如果原图有透明通道(PNG)，转为 RGB
            if (img.getType() == BufferedImage.TYPE_INT_ARGB || img.getType() == BufferedImage.TYPE_4BYTE_ABGR) {
                BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = rgb.createGraphics();
                g.drawImage(img, 0, 0, java.awt.Color.WHITE, null);
                g.dispose();
                img = rgb;
            }

            float[] qualities = {0.85f, 0.7f, 0.5f, 0.35f, 0.2f};
            for (float quality : qualities) {
                byte[] result = writeJpeg(img, quality);
                if (result != null && result.length <= maxBytes) {
                    return result;
                }
            }

            return writeJpeg(img, 0.1f);
        } catch (Exception e) {
            logger.warn("图片压缩异常: {}", e.getMessage());
            return null;
        }
    }

    private byte[] writeJpeg(BufferedImage img, float quality) {
        try {
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
            if (!writers.hasNext()) return null;
            ImageWriter writer = writers.next();

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageOutputStream ios = ImageIO.createImageOutputStream(bos);
            writer.setOutput(ios);

            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);

            writer.write(null, new IIOImage(img, null, null), param);
            writer.dispose();
            ios.close();
            return bos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
}
