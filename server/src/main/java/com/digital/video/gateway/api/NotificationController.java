package com.digital.video.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.digital.video.gateway.database.Database;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 通知控制器
 */
public class NotificationController {
    private static final Logger logger = LoggerFactory.getLogger(NotificationController.class);
    private final Database database;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NotificationController(Database database) {
        this.database = database;
    }

    public void getNotifications(Context ctx) {
        try {
            String limitParam = ctx.queryParam("limit");
            int limit = limitParam != null ? Integer.parseInt(limitParam) : 100;
            if (limit > 500) limit = 500;

            List<Map<String, Object>> notifications = database.getAllNotifications(limit);

            ctx.status(200);
            ctx.contentType("application/json");
            ctx.result(createSuccessResponse(notifications));
        } catch (Exception e) {
            logger.error("获取通知列表失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "获取通知列表失败: " + e.getMessage()));
        }
    }

    public void markAsRead(Context ctx) {
        try {
            Map<String, Object> body = objectMapper.readValue(ctx.body(), Map.class);
            String notificationId = (String) body.get("id");

            if (notificationId == null || notificationId.isEmpty()) {
                ctx.status(400);
                ctx.result(createErrorResponse(400, "通知ID不能为空"));
                return;
            }

            boolean success = database.markNotificationAsRead(notificationId);

            if (success) {
                ctx.status(200);
                ctx.contentType("application/json");
                ctx.result(createSuccessResponse(Map.of("message", "通知已标记为已读")));
            } else {
                ctx.status(404);
                ctx.result(createErrorResponse(404, "通知不存在"));
            }
        } catch (Exception e) {
            logger.error("标记通知为已读失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "标记通知为已读失败: " + e.getMessage()));
        }
    }

    public void markAllAsRead(Context ctx) {
        try {
            boolean success = database.markAllNotificationsAsRead();

            ctx.status(200);
            ctx.contentType("application/json");
            ctx.result(createSuccessResponse(Map.of("message", "所有通知已标记为已读", "success", success)));
        } catch (Exception e) {
            logger.error("标记所有通知为已读失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "标记所有通知为已读失败: " + e.getMessage()));
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
