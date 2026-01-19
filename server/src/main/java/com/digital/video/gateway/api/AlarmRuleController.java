package com.digital.video.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.digital.video.gateway.database.AlarmRule;
import com.digital.video.gateway.service.AlarmRuleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 报警规则控制器
 */
public class AlarmRuleController {
    private static final Logger logger = LoggerFactory.getLogger(AlarmRuleController.class);
    private final AlarmRuleService alarmRuleService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AlarmRuleController(AlarmRuleService alarmRuleService) {
        this.alarmRuleService = alarmRuleService;
    }

    /**
     * 获取规则列表
     * GET /api/alarm-rules
     */
    public String getAlarmRules(Request request, Response response) {
        try {
            String deviceId = request.queryParams("deviceId");
            String assemblyId = request.queryParams("assemblyId");
            String alarmType = request.queryParams("alarmType");
            String enabledStr = request.queryParams("enabled");
            Boolean enabled = enabledStr != null ? Boolean.parseBoolean(enabledStr) : null;
            
            List<AlarmRule> rules = alarmRuleService.getAlarmRules(deviceId, assemblyId, alarmType, enabled);
            List<Map<String, Object>> result = rules.stream()
                .map(AlarmRule::toMap)
                .collect(java.util.stream.Collectors.toList());
            
            response.status(200);
            return createSuccessResponse(result);
        } catch (Exception e) {
            logger.error("获取规则列表失败", e);
            response.status(500);
            return createErrorResponse(500, "获取规则列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取规则详情
     * GET /api/alarm-rules/:id
     */
    public String getAlarmRule(Request request, Response response) {
        try {
            String ruleId = request.params(":id");
            AlarmRule rule = alarmRuleService.getAlarmRule(ruleId);
            if (rule == null) {
                response.status(404);
                return createErrorResponse(404, "规则不存在");
            }
            
            response.status(200);
            return createSuccessResponse(rule.toMap());
        } catch (Exception e) {
            logger.error("获取规则详情失败", e);
            response.status(500);
            return createErrorResponse(500, "获取规则详情失败: " + e.getMessage());
        }
    }

    /**
     * 创建规则
     * POST /api/alarm-rules
     */
    public String createAlarmRule(Request request, Response response) {
        try {
            Map<String, Object> body = objectMapper.readValue(request.body(), Map.class);
            AlarmRule rule = new AlarmRule();
            rule.setName((String) body.get("name"));
            rule.setAlarmType((String) body.get("alarmType"));
            rule.setScope((String) body.get("scope"));
            rule.setDeviceId((String) body.get("deviceId"));
            rule.setAssemblyId((String) body.get("assemblyId"));
            rule.setEnabled(body.get("enabled") != null ? (Boolean) body.get("enabled") : true);
            rule.setPriority(body.get("priority") != null ? ((Number) body.get("priority")).intValue() : 0);
            rule.setFlowId((String) body.get("flowId"));
            // 处理 eventTypeIds（JSON数组）
            Object eventTypeIds = body.get("eventTypeIds");
            if (eventTypeIds != null) {
                rule.setEventTypeIds(objectMapper.writeValueAsString(eventTypeIds));
            }
            Object actions = body.get("actions");
            rule.setActions(objectMapper.writeValueAsString(actions != null ? actions : new java.util.HashMap<>()));
            rule.setConditions(body.get("conditions") != null ? objectMapper.writeValueAsString(body.get("conditions")) : null);
            
            AlarmRule created = alarmRuleService.createAlarmRule(rule);
            if (created == null) {
                response.status(500);
                return createErrorResponse(500, "创建规则失败");
            }
            
            response.status(201);
            return createSuccessResponse(created.toMap());
        } catch (Exception e) {
            logger.error("创建规则失败", e);
            response.status(500);
            return createErrorResponse(500, "创建规则失败: " + e.getMessage());
        }
    }

    /**
     * 更新规则
     * PUT /api/alarm-rules/:id
     */
    public String updateAlarmRule(Request request, Response response) {
        try {
            String ruleId = request.params(":id");
            Map<String, Object> body = objectMapper.readValue(request.body(), Map.class);
            AlarmRule rule = new AlarmRule();
            rule.setName((String) body.get("name"));
            rule.setAlarmType((String) body.get("alarmType"));
            rule.setScope((String) body.get("scope"));
            rule.setDeviceId((String) body.get("deviceId"));
            rule.setAssemblyId((String) body.get("assemblyId"));
            rule.setEnabled(body.get("enabled") != null ? (Boolean) body.get("enabled") : true);
            rule.setPriority(body.get("priority") != null ? ((Number) body.get("priority")).intValue() : 0);
            rule.setFlowId((String) body.get("flowId"));
            // 处理 eventTypeIds（JSON数组）
            Object eventTypeIds = body.get("eventTypeIds");
            if (eventTypeIds != null) {
                rule.setEventTypeIds(objectMapper.writeValueAsString(eventTypeIds));
            }
            Object actions = body.get("actions");
            rule.setActions(objectMapper.writeValueAsString(actions != null ? actions : new java.util.HashMap<>()));
            rule.setConditions(body.get("conditions") != null ? objectMapper.writeValueAsString(body.get("conditions")) : null);
            
            AlarmRule updated = alarmRuleService.updateAlarmRule(ruleId, rule);
            if (updated == null) {
                response.status(404);
                return createErrorResponse(404, "规则不存在");
            }
            
            response.status(200);
            return createSuccessResponse(updated.toMap());
        } catch (Exception e) {
            logger.error("更新规则失败", e);
            response.status(500);
            return createErrorResponse(500, "更新规则失败: " + e.getMessage());
        }
    }

    /**
     * 删除规则
     * DELETE /api/alarm-rules/:id
     */
    public String deleteAlarmRule(Request request, Response response) {
        try {
            String ruleId = request.params(":id");
            boolean deleted = alarmRuleService.deleteAlarmRule(ruleId);
            if (!deleted) {
                response.status(404);
                return createErrorResponse(404, "规则不存在");
            }
            
            response.status(200);
            return createSuccessResponse(Map.of("message", "删除成功"));
        } catch (Exception e) {
            logger.error("删除规则失败", e);
            response.status(500);
            return createErrorResponse(500, "删除规则失败: " + e.getMessage());
        }
    }

    /**
     * 启用/禁用规则
     * PUT /api/alarm-rules/:id/toggle
     */
    public String toggleRule(Request request, Response response) {
        try {
            String ruleId = request.params(":id");
            Map<String, Object> body = objectMapper.readValue(request.body(), Map.class);
            boolean enabled = body.get("enabled") != null ? (Boolean) body.get("enabled") : true;
            
            AlarmRule rule = alarmRuleService.toggleRule(ruleId, enabled);
            if (rule == null) {
                response.status(404);
                return createErrorResponse(404, "规则不存在");
            }
            
            response.status(200);
            return createSuccessResponse(rule.toMap());
        } catch (Exception e) {
            logger.error("切换规则状态失败", e);
            response.status(500);
            return createErrorResponse(500, "切换规则状态失败: " + e.getMessage());
        }
    }

    /**
     * 获取设备的所有规则
     * GET /api/devices/:deviceId/alarm-rules
     */
    public String getDeviceRules(Request request, Response response) {
        try {
            String deviceId = request.params(":deviceId");
            List<AlarmRule> rules = alarmRuleService.getDeviceRules(deviceId);
            List<Map<String, Object>> result = rules.stream()
                .map(AlarmRule::toMap)
                .collect(java.util.stream.Collectors.toList());
            
            response.status(200);
            return createSuccessResponse(result);
        } catch (Exception e) {
            logger.error("获取设备规则列表失败", e);
            response.status(500);
            return createErrorResponse(500, "获取设备规则列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取装置的所有规则
     * GET /api/assemblies/:assemblyId/alarm-rules
     */
    public String getAssemblyRules(Request request, Response response) {
        try {
            String assemblyId = request.params(":assemblyId");
            List<AlarmRule> rules = alarmRuleService.getAssemblyRules(assemblyId);
            List<Map<String, Object>> result = rules.stream()
                .map(AlarmRule::toMap)
                .collect(java.util.stream.Collectors.toList());
            
            response.status(200);
            return createSuccessResponse(result);
        } catch (Exception e) {
            logger.error("获取装置规则列表失败", e);
            response.status(500);
            return createErrorResponse(500, "获取装置规则列表失败: " + e.getMessage());
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
