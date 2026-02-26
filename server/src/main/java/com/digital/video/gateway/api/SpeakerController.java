package com.digital.video.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.digital.video.gateway.database.Speaker;
import com.digital.video.gateway.service.SpeakerService;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    public void getSpeakers(Context ctx) {
        try {
            List<Speaker> speakers = speakerService.getSpeakers();
            List<Map<String, Object>> result = speakers.stream().map(Speaker::toMap).collect(Collectors.toList());
            ctx.status(200);
            ctx.result(createSuccessResponse(result));
        } catch (Exception e) {
            logger.error("获取音柱列表失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "获取音柱列表失败: " + e.getMessage()));
        }
    }

    public void getSpeaker(Context ctx) {
        try {
            String deviceId = ctx.pathParam("deviceId");
            Speaker speaker = speakerService.getSpeaker(deviceId);
            if (speaker == null) {
                ctx.status(404);
                ctx.result(createErrorResponse(404, "音柱设备不存在"));
                return;
            }
            ctx.status(200);
            ctx.result(createSuccessResponse(speaker.toMap()));
        } catch (Exception e) {
            logger.error("获取音柱详情失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "获取音柱详情失败: " + e.getMessage()));
        }
    }

    public void createSpeaker(Context ctx) {
        try {
            Map<String, Object> body = objectMapper.readValue(ctx.body(), Map.class);
            Speaker speaker = new Speaker();
            speaker.setDeviceId((String) body.get("deviceId"));
            speaker.setName((String) body.get("name"));
            speaker.setApiEndpoint((String) body.get("apiEndpoint"));
            speaker.setApiType((String) body.getOrDefault("apiType", "http"));
            speaker.setApiConfig((String) body.get("apiConfig"));
            speaker.setStatus((String) body.getOrDefault("status", "offline"));

            Speaker created = speakerService.createSpeaker(speaker);
            if (created == null) {
                ctx.status(500);
                ctx.result(createErrorResponse(500, "创建音柱设备失败"));
                return;
            }
            ctx.status(201);
            ctx.result(createSuccessResponse(created.toMap()));
        } catch (Exception e) {
            logger.error("创建音柱设备失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "创建音柱设备失败: " + e.getMessage()));
        }
    }

    public void updateSpeaker(Context ctx) {
        try {
            String deviceId = ctx.pathParam("deviceId");
            Map<String, Object> body = objectMapper.readValue(ctx.body(), Map.class);
            Speaker speaker = new Speaker();
            speaker.setName((String) body.get("name"));
            speaker.setApiEndpoint((String) body.get("apiEndpoint"));
            speaker.setApiType((String) body.get("apiType"));
            speaker.setApiConfig((String) body.get("apiConfig"));
            speaker.setStatus((String) body.get("status"));

            Speaker updated = speakerService.updateSpeaker(deviceId, speaker);
            if (updated == null) {
                ctx.status(404);
                ctx.result(createErrorResponse(404, "音柱设备不存在"));
                return;
            }
            ctx.status(200);
            ctx.result(createSuccessResponse(updated.toMap()));
        } catch (Exception e) {
            logger.error("更新音柱设备失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "更新音柱设备失败: " + e.getMessage()));
        }
    }

    public void deleteSpeaker(Context ctx) {
        try {
            String deviceId = ctx.pathParam("deviceId");
            boolean deleted = speakerService.deleteSpeaker(deviceId);
            if (!deleted) {
                ctx.status(404);
                ctx.result(createErrorResponse(404, "音柱设备不存在"));
                return;
            }
            ctx.status(200);
            ctx.result(createSuccessResponse(Map.of("message", "删除成功")));
        } catch (Exception e) {
            logger.error("删除音柱设备失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "删除音柱设备失败: " + e.getMessage()));
        }
    }

    public void playVoice(Context ctx) {
        try {
            String deviceId = ctx.pathParam("deviceId");
            Map<String, Object> body = objectMapper.readValue(ctx.body(), Map.class);
            String text = (String) body.get("text");
            if (text == null || text.isEmpty()) {
                ctx.status(400);
                ctx.result(createErrorResponse(400, "语音文本不能为空"));
                return;
            }
            boolean success = speakerService.playVoice(deviceId, text);
            if (!success) {
                ctx.status(500);
                ctx.result(createErrorResponse(500, "播放语音失败"));
                return;
            }
            ctx.status(200);
            ctx.result(createSuccessResponse(Map.of("message", "播放成功")));
        } catch (Exception e) {
            logger.error("播放语音失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "播放语音失败: " + e.getMessage()));
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
