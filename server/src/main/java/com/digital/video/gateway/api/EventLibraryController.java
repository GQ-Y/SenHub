package com.digital.video.gateway.api;

import com.digital.video.gateway.database.CanonicalEventTable;
import com.digital.video.gateway.database.Database;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.*;

public class EventLibraryController {
    private static final Logger logger = LoggerFactory.getLogger(EventLibraryController.class);
    private final Database database;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EventLibraryController(Database database) {
        this.database = database;
    }

    public void listEvents(Context ctx) {
        try (Connection conn = database.getConnection()) {
            String eventKey = ctx.queryParam("eventKey");
            String category = ctx.queryParam("category");
            String brand = ctx.queryParam("brand");
            String enabledStr = ctx.queryParam("enabled");
            String isGenericStr = ctx.queryParam("isGeneric");

            Boolean enabled = enabledStr != null ? Boolean.parseBoolean(enabledStr) : null;
            Boolean isGeneric = isGenericStr != null ? Boolean.parseBoolean(isGenericStr) : null;

            List<Map<String, Object>> events = CanonicalEventTable.listCanonicalEvents(conn, eventKey, category, brand, enabled, isGeneric);

            for (Map<String, Object> event : events) {
                String ek = (String) event.get("eventKey");
                List<Map<String, Object>> mappings = CanonicalEventTable.getMappingsByEventKey(conn, ek);
                event.put("mappings", mappings);
            }

            ctx.json(Map.of("code", 200, "data", events));
        } catch (Exception e) {
            logger.error("查询事件库失败", e);
            ctx.status(500).json(Map.of("code", 500, "message", e.getMessage()));
        }
    }

    public void getEvent(Context ctx) {
        try (Connection conn = database.getConnection()) {
            int id = Integer.parseInt(ctx.pathParam("id"));
            Map<String, Object> event = CanonicalEventTable.getCanonicalEventById(conn, id);
            if (event == null) {
                ctx.status(404).json(Map.of("code", 404, "message", "事件不存在"));
                return;
            }
            String ek = (String) event.get("eventKey");
            event.put("mappings", CanonicalEventTable.getMappingsByEventKey(conn, ek));
            event.put("rawPayloads", CanonicalEventTable.getRawPayloadsByEventKey(conn, ek));
            ctx.json(Map.of("code", 200, "data", event));
        } catch (Exception e) {
            logger.error("查询事件详情失败", e);
            ctx.status(500).json(Map.of("code", 500, "message", e.getMessage()));
        }
    }

    @SuppressWarnings("unchecked")
    public void createEvent(Context ctx) {
        try (Connection conn = database.getConnection()) {
            Map<String, Object> body = objectMapper.readValue(ctx.body(), Map.class);
            String eventKey = (String) body.get("eventKey");
            String nameZh = (String) body.get("nameZh");
            if (eventKey == null || eventKey.isEmpty() || nameZh == null || nameZh.isEmpty()) {
                ctx.status(400).json(Map.of("code", 400, "message", "eventKey 和 nameZh 不能为空"));
                return;
            }
            String nameEn = (String) body.get("nameEn");
            String category = (String) body.get("category");
            String severity = (String) body.get("severity");
            String description = (String) body.get("description");
            boolean isGeneric = Boolean.TRUE.equals(body.get("isGeneric"));
            String aiVerifyPrompt = (String) body.get("aiVerifyPrompt");

            int newId = CanonicalEventTable.insertCanonicalEvent(conn, eventKey, nameZh, nameEn,
                    category, severity, description, isGeneric, aiVerifyPrompt);

            // 可选：批量插入映射
            Object mappingsObj = body.get("mappings");
            if (mappingsObj instanceof List) {
                for (Object item : (List<?>) mappingsObj) {
                    if (item instanceof Map) {
                        Map<String, Object> m = (Map<String, Object>) item;
                        String brand = (String) m.get("brand");
                        String sourceKind = (String) m.get("sourceKind");
                        int sourceCode = m.get("sourceCode") instanceof Number ? ((Number) m.get("sourceCode")).intValue() : 0;
                        int priority = m.get("priority") instanceof Number ? ((Number) m.get("priority")).intValue() : 0;
                        String note = (String) m.get("note");
                        if (brand != null && sourceKind != null) {
                            CanonicalEventTable.insertMapping(conn, brand, sourceKind, sourceCode, eventKey, priority, note);
                        }
                    }
                }
            }

            Map<String, Object> created = CanonicalEventTable.getCanonicalEventById(conn, newId);
            ctx.status(201).json(Map.of("code", 201, "data", created != null ? created : Map.of("id", newId)));
        } catch (Exception e) {
            logger.error("创建事件失败", e);
            ctx.status(500).json(Map.of("code", 500, "message", e.getMessage()));
        }
    }

    @SuppressWarnings("unchecked")
    public void updateEvent(Context ctx) {
        try (Connection conn = database.getConnection()) {
            int id = Integer.parseInt(ctx.pathParam("id"));
            Map<String, Object> body = objectMapper.readValue(ctx.body(), Map.class);

            String nameZh = (String) body.get("nameZh");
            String nameEn = (String) body.get("nameEn");
            String category = (String) body.get("category");
            String severity = (String) body.get("severity");
            String description = (String) body.get("description");
            Boolean enabled = body.containsKey("enabled") ? Boolean.valueOf(body.get("enabled").toString()) : null;
            Boolean isGeneric = body.containsKey("isGeneric") ? Boolean.valueOf(body.get("isGeneric").toString()) : null;
            String aiVerifyPrompt = body.containsKey("aiVerifyPrompt") ? (String) body.get("aiVerifyPrompt") : null;

            boolean updated = CanonicalEventTable.updateCanonicalEvent(conn, id, nameZh, nameEn,
                    category, severity, description, enabled, isGeneric, aiVerifyPrompt);

            if (!updated) {
                ctx.status(404).json(Map.of("code", 404, "message", "事件不存在"));
                return;
            }
            Map<String, Object> event = CanonicalEventTable.getCanonicalEventById(conn, id);
            ctx.json(Map.of("code", 200, "data", event));
        } catch (Exception e) {
            logger.error("更新事件失败", e);
            ctx.status(500).json(Map.of("code", 500, "message", e.getMessage()));
        }
    }

    public void deleteEvent(Context ctx) {
        try (Connection conn = database.getConnection()) {
            int id = Integer.parseInt(ctx.pathParam("id"));
            boolean deleted = CanonicalEventTable.deleteCanonicalEvent(conn, id);
            if (!deleted) {
                ctx.status(404).json(Map.of("code", 404, "message", "事件不存在"));
                return;
            }
            ctx.json(Map.of("code", 200, "message", "删除成功"));
        } catch (Exception e) {
            logger.error("删除事件失败", e);
            ctx.status(500).json(Map.of("code", 500, "message", e.getMessage()));
        }
    }

    @SuppressWarnings("unchecked")
    public void addMapping(Context ctx) {
        try (Connection conn = database.getConnection()) {
            int eventId = Integer.parseInt(ctx.pathParam("id"));
            Map<String, Object> event = CanonicalEventTable.getCanonicalEventById(conn, eventId);
            if (event == null) {
                ctx.status(404).json(Map.of("code", 404, "message", "事件不存在"));
                return;
            }
            String eventKey = (String) event.get("eventKey");
            Map<String, Object> body = objectMapper.readValue(ctx.body(), Map.class);
            String brand = (String) body.get("brand");
            String sourceKind = (String) body.get("sourceKind");
            int sourceCode = body.get("sourceCode") instanceof Number ? ((Number) body.get("sourceCode")).intValue() : 0;
            int priority = body.get("priority") instanceof Number ? ((Number) body.get("priority")).intValue() : 0;
            String note = (String) body.get("note");

            if (brand == null || sourceKind == null) {
                ctx.status(400).json(Map.of("code", 400, "message", "brand 和 sourceKind 不能为空"));
                return;
            }

            int newId = CanonicalEventTable.insertMapping(conn, brand, sourceKind, sourceCode, eventKey, priority, note);
            ctx.status(201).json(Map.of("code", 201, "data", Map.of("id", newId)));
        } catch (Exception e) {
            logger.error("添加映射失败", e);
            ctx.status(500).json(Map.of("code", 500, "message", e.getMessage()));
        }
    }

    public void deleteMapping(Context ctx) {
        try (Connection conn = database.getConnection()) {
            int mappingId = Integer.parseInt(ctx.pathParam("mappingId"));
            boolean deleted = CanonicalEventTable.deleteMapping(conn, mappingId);
            if (!deleted) {
                ctx.status(404).json(Map.of("code", 404, "message", "映射不存在"));
                return;
            }
            ctx.json(Map.of("code", 200, "message", "删除成功"));
        } catch (Exception e) {
            logger.error("删除映射失败", e);
            ctx.status(500).json(Map.of("code", 500, "message", e.getMessage()));
        }
    }
}
