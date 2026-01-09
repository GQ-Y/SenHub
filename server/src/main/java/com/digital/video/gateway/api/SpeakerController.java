package com.digital.video.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.digital.video.gateway.database.Speaker;
import com.digital.video.gateway.service.SpeakerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 音柱设备控制器
 */
public class SpeakerController {
    private static final Logger logger = LoggerFactory.getLogger(SpeakerController.class);
    private final SpeakerService speakerService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SpeakerController(SpeakerService speakerService) {
        this.speakerService = speakerService;
    }

    /**
     * 获取音柱列表
     * GET /api/speakers
     */
    public String getSpeakers(Request request, Response response) {
        try {
            List<Speaker> speakers = speakerService.getSpeakers();
            List<Map<String, Object>> result = speakers.stream()
                .map(Speaker::toMap)
                .collect(java.util.stream.Collectors.toList());
            
            response.status(200);
            return createSuccessResponse(result);
        } catch (Exception e) {
            logger.error("获取音柱列表失败", e);
            response.status(500);
            return createErrorResponse(500, "获取音柱列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取音柱详情
     * GET /api/speakers/:deviceId
     */
    public String getSpeaker(Request request, Response response) {
        try {
            String deviceId = request.params(":deviceId");
            Speaker speaker = speakerService.getSpeaker(deviceId);
            if (speaker == null) {
                response.status(404);
                return createErrorResponse(404, "音柱设备不存在");
            }
            
            response.status(200);
            return createSuccessResponse(speaker.toMap());
        } catch (Exception e) {
            logger.error("获取音柱详情失败", e);
            response.status(500);
            return createErrorResponse(500, "获取音柱详情失败: " + e.getMessage());
        }
    }

    /**
     * 创建音柱设备
     * POST /api/speakers
     */
    public String createSpeaker(Request request, Response response) {
        try {
            Map<String, Object> body = objectMapper.readValue(request.body(), Map.class);
            Speaker speaker = new Speaker();
            speaker.setDeviceId((String) body.get("deviceId"));
            speaker.setName((String) body.get("name"));
            speaker.setApiEndpoint((String) body.get("apiEndpoint"));
            speaker.setApiType((String) body.getOrDefault("apiType", "http"));
            speaker.setApiConfig((String) body.get("apiConfig"));
            speaker.setStatus((String) body.getOrDefault("status", "offline"));
            
            Speaker created = speakerService.createSpeaker(speaker);
            if (created == null) {
                response.status(500);
                return createErrorResponse(500, "创建音柱设备失败");
            }
            
            response.status(201);
            return createSuccessResponse(created.toMap());
        } catch (Exception e) {
            logger.error("创建音柱设备失败", e);
            response.status(500);
            return createErrorResponse(500, "创建音柱设备失败: " + e.getMessage());
        }
    }

    /**
     * 更新音柱设备
     * PUT /api/speakers/:deviceId
     */
    public String updateSpeaker(Request request, Response response) {
        try {
            String deviceId = request.params(":deviceId");
            Map<String, Object> body = objectMapper.readValue(request.body(), Map.class);
            Speaker speaker = new Speaker();
            speaker.setName((String) body.get("name"));
            speaker.setApiEndpoint((String) body.get("apiEndpoint"));
            speaker.setApiType((String) body.get("apiType"));
            speaker.setApiConfig((String) body.get("apiConfig"));
            speaker.setStatus((String) body.get("status"));
            
            Speaker updated = speakerService.updateSpeaker(deviceId, speaker);
            if (updated == null) {
                response.status(404);
                return createErrorResponse(404, "音柱设备不存在");
            }
            
            response.status(200);
            return createSuccessResponse(updated.toMap());
        } catch (Exception e) {
            logger.error("更新音柱设备失败", e);
            response.status(500);
            return createErrorResponse(500, "更新音柱设备失败: " + e.getMessage());
        }
    }

    /**
     * 删除音柱设备
     * DELETE /api/speakers/:deviceId
     */
    public String deleteSpeaker(Request request, Response response) {
        try {
            String deviceId = request.params(":deviceId");
            boolean deleted = speakerService.deleteSpeaker(deviceId);
            if (!deleted) {
                response.status(404);
                return createErrorResponse(404, "音柱设备不存在");
            }
            
            response.status(200);
            return createSuccessResponse(Map.of("message", "删除成功"));
        } catch (Exception e) {
            logger.error("删除音柱设备失败", e);
            response.status(500);
            return createErrorResponse(500, "删除音柱设备失败: " + e.getMessage());
        }
    }

    /**
     * 播放语音
     * POST /api/speakers/:deviceId/play
     */
    public String playVoice(Request request, Response response) {
        try {
            String deviceId = request.params(":deviceId");
            Map<String, Object> body = objectMapper.readValue(request.body(), Map.class);
            String text = (String) body.get("text");
            
            if (text == null || text.isEmpty()) {
                response.status(400);
                return createErrorResponse(400, "语音文本不能为空");
            }
            
            boolean success = speakerService.playVoice(deviceId, text);
            if (!success) {
                response.status(500);
                return createErrorResponse(500, "播放语音失败");
            }
            
            response.status(200);
            return createSuccessResponse(Map.of("message", "播放成功"));
        } catch (Exception e) {
            logger.error("播放语音失败", e);
            response.status(500);
            return createErrorResponse(500, "播放语音失败: " + e.getMessage());
        }
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
