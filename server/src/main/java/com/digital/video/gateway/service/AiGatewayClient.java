package com.digital.video.gateway.service;

import com.digital.video.gateway.config.Config;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * AI 网关统一客户端：封装 OpenRouter/OneAPI/NewAPI 兼容的 chat/completions 调用。
 * 从 ConfigService 读取 baseUrl、apiKey、defaultModel，支持纯文本、多模态和 function call。
 */
public class AiGatewayClient {
    private static final Logger logger = LoggerFactory.getLogger(AiGatewayClient.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final ConfigService configService;
    private final HttpClient httpClient;

    public AiGatewayClient(ConfigService configService) {
        this.configService = configService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    private Config.AiConfig getAiConfig() {
        Config config = configService != null ? configService.getConfig() : null;
        return config != null ? config.getAi() : null;
    }

    /**
     * 纯文本 chat completion，返回 assistant content 字符串。
     */
    public String chatCompletion(List<Map<String, Object>> messages, String model, int maxTokens) throws Exception {
        Map<String, Object> body = buildChatBody(messages, model, maxTokens, null, null);
        String responseJson = postChatCompletions(body);
        return extractContentFromResponse(responseJson);
    }

    /**
     * 多模态：URL 图片 + 文本提示。
     */
    public String chatCompletionWithImage(String textPrompt, String imageUrl, String model) throws Exception {
        List<Map<String, Object>> content = List.of(
                Map.<String, Object>of("type", "text", "text", textPrompt != null ? textPrompt : ""),
                Map.<String, Object>of("type", "image_url", "image_url", Map.of("url", imageUrl))
        );
        List<Map<String, Object>> messages = List.of(
                Map.<String, Object>of("role", "user", "content", content)
        );
        Map<String, Object> body = buildChatBody(messages, model, 512, null, null);
        String responseJson = postChatCompletions(body);
        return extractContentFromResponse(responseJson);
    }

    /**
     * 多模态：base64 图片 + 文本提示。
     */
    public String chatCompletionWithBase64Image(String textPrompt, String base64Data, String mimeType, String model) throws Exception {
        String dataUrl = "data:" + (mimeType != null ? mimeType : "image/jpeg") + ";base64," + base64Data;
        List<Map<String, Object>> content = List.of(
                Map.<String, Object>of("type", "text", "text", textPrompt != null ? textPrompt : ""),
                Map.<String, Object>of("type", "image_url", "image_url", Map.of("url", dataUrl))
        );
        List<Map<String, Object>> messages = List.of(
                Map.<String, Object>of("role", "user", "content", content)
        );
        Map<String, Object> body = buildChatBody(messages, model, 512, null, null);
        String responseJson = postChatCompletions(body);
        return extractContentFromResponse(responseJson);
    }

    /**
     * 带 tools（function call）的请求，返回完整响应 JSON 字符串，供调用方解析 tool_calls。
     */
    public String chatCompletionWithTools(List<Map<String, Object>> messages, List<Map<String, Object>> tools,
                                          Map<String, Object> toolChoice, String model) throws Exception {
        Map<String, Object> body = buildChatBody(messages, model, 512, tools, toolChoice);
        return postChatCompletions(body);
    }

    private String resolveModel(String model) {
        if (model != null && !model.isBlank()) return model;
        Config.AiConfig ai = getAiConfig();
        if (ai != null && ai.getDefaultModel() != null && !ai.getDefaultModel().isBlank()) {
            return ai.getDefaultModel();
        }
        return "google/gemini-2.0-flash-001";
    }

    private Map<String, Object> buildChatBody(List<Map<String, Object>> messages, String model, int maxTokens,
                                              List<Map<String, Object>> tools, Map<String, Object> toolChoice) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("model", resolveModel(model));
        body.put("messages", messages);
        body.put("max_tokens", maxTokens > 0 ? maxTokens : 512);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
            if (toolChoice != null && !toolChoice.isEmpty()) {
                body.put("tool_choice", toolChoice);
            }
        }
        return body;
    }

    private String postChatCompletions(Map<String, Object> body) throws Exception {
        Config.AiConfig ai = getAiConfig();
        if (ai == null || !ai.isEnabled()) {
            throw new IllegalStateException("AI 服务未启用或未配置");
        }
        String baseUrl = ai.getBaseUrl();
        String apiKey = ai.getApiKey();
        if (baseUrl == null || baseUrl.isBlank() || apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("AI 网关 baseUrl 或 apiKey 未配置");
        }
        String url = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";
        String bodyJson = mapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("AI 网关请求失败: HTTP " + response.statusCode() + " " + response.body());
        }
        return response.body();
    }

    private String extractContentFromResponse(String responseJson) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> root = mapper.readValue(responseJson, Map.class);
        List<?> choices = (List<?>) root.get("choices");
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) choices.get(0);
        Map<String, Object> message = (Map<String, Object>) first.get("message");
        if (message == null) {
            return null;
        }
        Object content = message.get("content");
        return content != null ? content.toString().trim() : null;
    }
}
