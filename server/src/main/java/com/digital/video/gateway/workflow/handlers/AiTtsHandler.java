package com.digital.video.gateway.workflow.handlers;

import com.digital.video.gateway.config.Config;
import com.digital.video.gateway.service.ConfigService;
import com.digital.video.gateway.workflow.FlowContext;
import com.digital.video.gateway.workflow.FlowNodeDefinition;
import com.digital.video.gateway.workflow.FlowNodeHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * AI 语音合成节点：调用 MiniMax TTS API 将文本转为 MP3，保存到临时文件，路径写入 context.ai_tts_audio_path。
 */
public class AiTtsHandler implements FlowNodeHandler {
    private static final Logger logger = LoggerFactory.getLogger(AiTtsHandler.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String TTS_URL = "https://api.minimaxi.com/v1/t2a_v2";

    private final ConfigService configService;

    public AiTtsHandler(ConfigService configService) {
        this.configService = configService;
    }

    @Override
    public boolean execute(FlowNodeDefinition node, FlowContext context) throws Exception {
        String text = getTextToSpeak(node, context);
        if (text == null || text.isBlank()) {
            logger.warn("ai_tts 无播报文本，跳过");
            return true;
        }

        Config config = configService != null ? configService.getConfig() : null;
        Config.AiConfig ai = config != null ? config.getAi() : null;
        if (ai == null || ai.getTtsApiKey() == null || ai.getTtsApiKey().isBlank()) {
            logger.warn("MiniMax TTS 未配置 ttsApiKey，跳过");
            return true;
        }

        String voice = ai.getTtsVoice();
        String model = ai.getTtsModel() != null && !ai.getTtsModel().isBlank() ? ai.getTtsModel() : "speech-02-hd";
        Map<String, Object> cfg = node.getConfig();
        if (cfg != null && cfg.get("voice") instanceof String) {
            String v = ((String) cfg.get("voice")).trim();
            if (!v.isEmpty()) voice = v;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("text", text);
        body.put("stream", false);
        body.put("voice_setting", Map.of(
                "voice_id", voice != null ? voice : "male-qn-qingse",
                "speed", 1,
                "vol", 1,
                "pitch", 0
        ));
        body.put("audio_setting", Map.of(
                "sample_rate", 32000,
                "bitrate", 128000,
                "format", "mp3",
                "channel", 1
        ));

        try {
            String bodyJson = mapper.writeValueAsString(body);
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(TTS_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + ai.getTtsApiKey())
                    .timeout(java.time.Duration.ofSeconds(30))
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(bodyJson))
                    .build();

            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(10))
                    .build();
            java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.warn("MiniMax TTS 请求失败: {} {}", response.statusCode(), response.body());
                return true;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> root = mapper.readValue(response.body(), Map.class);
            Map<String, Object> data = (Map<String, Object>) root.get("data");
            if (data == null) {
                logger.warn("MiniMax TTS 响应无 data");
                return true;
            }
            Object audioObj = data.get("audio");
            if (audioObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> audio = (Map<String, Object>) audioObj;
                String hexData = (String) audio.get("data");
                if (hexData != null && !hexData.isBlank()) {
                    byte[] bytes = hexToBytes(hexData);
                    Path tempFile = Files.createTempFile("tts_", ".mp3");
                    Files.write(tempFile, bytes);
                    String path = tempFile.toAbsolutePath().toString();
                    context.putVariable("ai_tts_audio_path", path);
                    logger.info("ai_tts 合成成功: {} 字节 -> {}", bytes.length, path);
                    return true;
                }
            }
            logger.warn("MiniMax TTS 响应中未找到 audio.data");
        } catch (Exception e) {
            logger.warn("ai_tts 调用失败: {}", e.getMessage());
        }
        return true;
    }

    private String getTextToSpeak(FlowNodeDefinition node, FlowContext context) {
        Map<String, Object> cfg = node.getConfig();
        if (cfg != null && cfg.get("text") instanceof String) {
            String template = ((String) cfg.get("text")).trim();
            if (!template.isEmpty()) {
                Map<String, Object> extra = new HashMap<>();
                if (context.getVariables() != null) {
                    extra.putAll(context.getVariables());
                }
                String rendered = HandlerUtils.renderTemplate(template, context, extra);
                if (rendered != null && !rendered.isBlank()) return rendered;
            }
        }
        if (context.getVariables() != null && context.getVariables().get("ai_alert_text") instanceof String) {
            return (String) context.getVariables().get("ai_alert_text");
        }
        return "检测到" + (context.getAlarmType() != null ? context.getAlarmType() : "报警") + "，请注意。";
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
