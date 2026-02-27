package com.digital.video.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.digital.video.gateway.database.CanonicalEventTable;
import com.digital.video.gateway.database.Database;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 事件类型控制器
 * 统一从 canonical_events 提供事件数据，不再依赖 camera_event_types
 */
public class EventTypeController {
    private static final Logger logger = LoggerFactory.getLogger(EventTypeController.class);
    private final Database database;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EventTypeController(Database database) {
        this.database = database;
    }

    /**
     * 获取所有事件类型（按 category 分组）
     * GET /api/event-types
     *
     * 返回结构：
     * {
     *   "code": 200,
     *   "data": {
     *     "events": [ { eventKey, nameZh, nameEn, category, severity, ... } ],
     *     "grouped": { "basic": [...], "vca": [...], ... }
     *   }
     * }
     */
    public void getAllEventTypes(Context ctx) {
        try {
            Connection conn = database.getConnection();

            List<Map<String, Object>> allEvents = CanonicalEventTable.getAllCanonicalEventsWithBrands(conn);

            Map<String, List<Map<String, Object>>> grouped = allEvents.stream()
                    .collect(Collectors.groupingBy(
                            e -> (String) e.getOrDefault("category", "other"),
                            Collectors.toList()));

            // 收集所有出现过的品牌
            java.util.Set<String> allBrands = new java.util.LinkedHashSet<>();
            for (Map<String, Object> ev : allEvents) {
                @SuppressWarnings("unchecked")
                List<String> brands = (List<String>) ev.get("brands");
                if (brands != null) allBrands.addAll(brands);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("events", allEvents);
            result.put("grouped", grouped);
            result.put("brands", new java.util.ArrayList<>(allBrands));

            ctx.status(200);
            ctx.contentType("application/json");
            ctx.result(createSuccessResponse(result));
        } catch (Exception e) {
            logger.error("获取事件类型列表失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "获取事件类型列表失败: " + e.getMessage()));
        }
    }

    /**
     * 获取所有事件类型（平铺列表）
     * GET /api/event-types/all
     */
    public void getAllEventTypesList(Context ctx) {
        try {
            Connection conn = database.getConnection();
            List<Map<String, Object>> allEvents = CanonicalEventTable.getAllCanonicalEventsWithBrands(conn);
            ctx.status(200);
            ctx.contentType("application/json");
            ctx.result(createSuccessResponse(allEvents));
        } catch (Exception e) {
            logger.error("获取所有事件类型失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "获取所有事件类型失败: " + e.getMessage()));
        }
    }

    /**
     * 获取指定品牌的事件类型（通过品牌映射过滤 canonical_events）
     * GET /api/event-types/{brand}
     */
    public void getEventTypesByBrand(Context ctx) {
        try {
            String brand = ctx.pathParam("brand");
            if (brand == null || brand.isEmpty()) {
                ctx.status(400);
                ctx.result(createErrorResponse(400, "品牌参数不能为空"));
                return;
            }
            Connection conn = database.getConnection();
            List<Map<String, Object>> events = CanonicalEventTable.listCanonicalEvents(
                    conn, null, null, brand.toLowerCase(), true, null);
            ctx.status(200);
            ctx.contentType("application/json");
            ctx.result(createSuccessResponse(events));
        } catch (Exception e) {
            logger.error("获取品牌事件类型失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "获取品牌事件类型失败: " + e.getMessage()));
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
