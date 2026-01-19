package com.digital.video.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.digital.video.gateway.database.CameraEventTypeTable;
import com.digital.video.gateway.database.Database;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 事件类型控制器
 * 提供摄像头报警事件类型查询接口
 */
public class EventTypeController {
    private static final Logger logger = LoggerFactory.getLogger(EventTypeController.class);
    private final Database database;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EventTypeController(Database database) {
        this.database = database;
    }

    /**
     * 获取所有事件类型（按品牌分组）
     * GET /api/event-types
     */
    public String getAllEventTypes(Request request, Response response) {
        try {
            Connection conn = database.getConnection();
            
            // 获取所有品牌的事件类型
            Map<String, List<Map<String, Object>>> groupedEvents = new HashMap<>();
            
            // 海康威视
            List<Map<String, Object>> hikvisionEvents = CameraEventTypeTable.getEventTypesByBrand(conn, "hikvision");
            if (!hikvisionEvents.isEmpty()) {
                groupedEvents.put("hikvision", hikvisionEvents);
            }
            
            // 天地伟业
            List<Map<String, Object>> tiandyEvents = CameraEventTypeTable.getEventTypesByBrand(conn, "tiandy");
            if (!tiandyEvents.isEmpty()) {
                groupedEvents.put("tiandy", tiandyEvents);
            }
            
            // 大华
            List<Map<String, Object>> dahuaEvents = CameraEventTypeTable.getEventTypesByBrand(conn, "dahua");
            if (!dahuaEvents.isEmpty()) {
                groupedEvents.put("dahua", dahuaEvents);
            }
            
            response.status(200);
            response.type("application/json");
            return createSuccessResponse(groupedEvents);
        } catch (Exception e) {
            logger.error("获取事件类型列表失败", e);
            response.status(500);
            return createErrorResponse(500, "获取事件类型列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取指定品牌的事件类型
     * GET /api/event-types/:brand
     */
    public String getEventTypesByBrand(Request request, Response response) {
        try {
            String brand = request.params(":brand");
            if (brand == null || brand.isEmpty()) {
                response.status(400);
                return createErrorResponse(400, "品牌参数不能为空");
            }
            
            Connection conn = database.getConnection();
            List<Map<String, Object>> events = CameraEventTypeTable.getEventTypesByBrand(conn, brand.toLowerCase());
            
            response.status(200);
            response.type("application/json");
            return createSuccessResponse(events);
        } catch (Exception e) {
            logger.error("获取品牌事件类型失败", e);
            response.status(500);
            return createErrorResponse(500, "获取品牌事件类型失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有事件类型（平铺列表）
     * GET /api/event-types/all
     */
    public String getAllEventTypesList(Request request, Response response) {
        try {
            Connection conn = database.getConnection();
            List<Map<String, Object>> allEvents = CameraEventTypeTable.getAllEventTypes(conn);
            
            response.status(200);
            response.type("application/json");
            return createSuccessResponse(allEvents);
        } catch (Exception e) {
            logger.error("获取所有事件类型失败", e);
            response.status(500);
            return createErrorResponse(500, "获取所有事件类型失败: " + e.getMessage());
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
