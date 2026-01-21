package com.digital.video.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.digital.video.gateway.database.CameraEventTypeTable;
import com.digital.video.gateway.database.CanonicalEventTable;
import com.digital.video.gateway.database.Database;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.sql.Connection;
import java.util.ArrayList;
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
     * 支持查询参数：
     *   - standard=true: 返回标准事件（canonical_events）和品牌映射
     *   - brand=xxx: 仅返回指定品牌的映射
     */
    public String getAllEventTypes(Request request, Response response) {
        try {
            Connection conn = database.getConnection();
            String standardParam = request.queryParams("standard");
            String brandParam = request.queryParams("brand");
            
            // 如果请求标准事件格式
            if ("true".equalsIgnoreCase(standardParam)) {
                Map<String, Object> result = new HashMap<>();
                
                // 获取所有标准事件
                List<Map<String, Object>> canonicalEvents = CanonicalEventTable.getAllCanonicalEvents(conn);
                result.put("canonicalEvents", canonicalEvents);
                
                // 获取品牌映射（如果指定了brand，只返回该品牌的映射）
                if (brandParam != null && !brandParam.isEmpty()) {
                    List<Map<String, Object>> mappings = CanonicalEventTable.getBrandMappings(conn, brandParam);
                    result.put("brandMappings", Map.of(brandParam, mappings));
                } else {
                    Map<String, List<Map<String, Object>>> allMappings = new HashMap<>();
                    allMappings.put("tiandy", CanonicalEventTable.getBrandMappings(conn, "tiandy"));
                    allMappings.put("hikvision", CanonicalEventTable.getBrandMappings(conn, "hikvision"));
                    result.put("brandMappings", allMappings);
                }
                
                response.status(200);
                response.type("application/json");
                return createSuccessResponse(result);
            }
            
            // 返回所有独立事件，包括基础报警和智能分析的二级事件
            // 策略：合并brand_event_mapping（标准事件映射）和camera_event_types（完整事件列表）
            // 优先使用brand_event_mapping中的事件（有标准事件映射），补充camera_event_types中未映射的事件
            Map<String, List<Map<String, Object>>> groupedEvents = new HashMap<>();
            
            String[] brands = {"tiandy", "hikvision", "dahua"};
            for (String brand : brands) {
                // 1. 从品牌映射生成独立事件（包括基础报警和智能分析的二级事件）
                Map<String, Map<String, Object>> mappedEvents = new HashMap<>(); // key: sourceKind_sourceCode
                List<Map<String, Object>> brandMappings = CanonicalEventTable.getBrandMappings(conn, brand);
                
                int baseId = brand.equals("tiandy") ? 1000 : (brand.equals("hikvision") ? 2000 : 3000);
                int eventId = baseId;
                
                for (Map<String, Object> mapping : brandMappings) {
                    String sourceKind = (String) mapping.get("sourceKind");
                    Integer sourceCode = (Integer) mapping.get("sourceCode");
                    String key = sourceKind + "_" + sourceCode;
                    
                    Map<String, Object> event = new HashMap<>();
                    event.put("id", eventId++);
                    event.put("brand", brand);
                    event.put("eventCode", sourceCode);
                    event.put("eventName", mapping.get("eventNameZh")); // 使用标准事件的中文名称
                    event.put("eventNameEn", mapping.get("eventNameEn")); // 使用标准事件的英文名称
                    event.put("category", mapping.get("category"));
                    event.put("description", mapping.get("note")); // 使用映射的备注作为描述
                    event.put("enabled", true);
                    event.put("sourceKind", sourceKind); // 标识来源类型（alarm_type/vca_event等）
                    event.put("eventKey", mapping.get("eventKey")); // 标准事件键
                    
                    mappedEvents.put(key, event);
                }
                
                // 2. 从camera_event_types获取所有事件（作为补充）
                List<Map<String, Object>> legacyEvents = CameraEventTypeTable.getEventTypesByBrand(conn, brand);
                for (Map<String, Object> legacyEvent : legacyEvents) {
                    Integer eventCode = (Integer) legacyEvent.get("eventCode");
                    // 尝试匹配：检查是否已经在mappedEvents中（通过eventCode匹配）
                    // 注意：camera_event_types中的eventCode可能对应多个sourceKind，需要智能匹配
                    boolean found = false;
                    for (Map<String, Object> mappedEvent : mappedEvents.values()) {
                        if (mappedEvent.get("eventCode").equals(eventCode)) {
                            found = true;
                            break;
                        }
                    }
                    
                    // 如果未找到匹配，添加为补充事件
                    // 对于天地伟业，eventCode 0-10可能是混合事件，需要特殊处理
                    if (!found) {
                        Map<String, Object> event = new HashMap<>(legacyEvent);
                        // 确保有必要的字段
                        if (!event.containsKey("sourceKind")) {
                            // 根据eventCode和category推断sourceKind
                            String inferredSourceKind = inferSourceKind(brand, eventCode, (String) event.get("category"));
                            event.put("sourceKind", inferredSourceKind);
                        }
                        mappedEvents.put("legacy_" + eventCode, event);
                    }
                }
                
                // 3. 转换为列表并排序
                List<Map<String, Object>> brandEvents = new ArrayList<>(mappedEvents.values());
                // 按category和eventCode排序
                brandEvents.sort((a, b) -> {
                    String catA = (String) a.getOrDefault("category", "");
                    String catB = (String) b.getOrDefault("category", "");
                    int catCompare = catA.compareTo(catB);
                    if (catCompare != 0) return catCompare;
                    Integer codeA = (Integer) a.get("eventCode");
                    Integer codeB = (Integer) b.get("eventCode");
                    return codeA.compareTo(codeB);
                });
                
                if (!brandEvents.isEmpty()) {
                    groupedEvents.put(brand, brandEvents);
                }
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
    
    /**
     * 根据品牌、eventCode和category推断sourceKind
     * 用于处理camera_event_types中未映射的事件
     */
    private String inferSourceKind(String brand, int eventCode, String category) {
        if ("tiandy".equals(brand)) {
            // 天地伟业：eventCode 0-131通常是vca_event（智能分析），200+是alarm_type（扩展报警）
            if (eventCode >= 0 && eventCode <= 131) {
                // 根据category判断：如果是basic，可能是alarm_type；如果是vca/face/its，是vca_event
                if ("basic".equals(category)) {
                    // eventCode 0-10可能是混合事件，但优先作为vca_event（因为智能分析更具体）
                    return "vca_event";
                } else {
                    return "vca_event";
                }
            } else if (eventCode >= 200) {
                return "alarm_type";
            }
        } else if ("hikvision".equals(brand)) {
            // 海康：eventCode 0-10是basic（alarm_type），100+是vca_event，200+是face，300+是its
            if (eventCode < 100) {
                return "alarm_type";
            } else if (eventCode < 200) {
                return "vca_event";
            } else if (eventCode < 300) {
                return "face";
            } else {
                return "its";
            }
        }
        // 默认返回vca_event
        return "vca_event";
    }
}
