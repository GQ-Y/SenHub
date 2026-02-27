package com.digital.video.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.digital.video.gateway.database.AlarmRule;
import com.digital.video.gateway.service.AlarmRuleService;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 报警规则控制器
 * 事件选择统一使用 eventKeys（canonical_events 标准事件键）
 */
public class AlarmRuleController {
    private static final Logger logger = LoggerFactory.getLogger(AlarmRuleController.class);
    private final AlarmRuleService alarmRuleService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AlarmRuleController(AlarmRuleService alarmRuleService) {
        this.alarmRuleService = alarmRuleService;
    }

    /**
     * GET /api/alarm-rules
     */
    public void getAlarmRules(Context ctx) {
        try {
            String deviceId = ctx.queryParam("deviceId");
            String assemblyId = ctx.queryParam("assemblyId");
            String alarmType = ctx.queryParam("alarmType");
            String enabledStr = ctx.queryParam("enabled");
            Boolean enabled = enabledStr != null ? Boolean.parseBoolean(enabledStr) : null;

            List<AlarmRule> rules = alarmRuleService.getAlarmRules(deviceId, assemblyId, alarmType, enabled);
            List<Map<String, Object>> result = rules.stream()
                .map(this::ruleToResponseMap)
                .collect(java.util.stream.Collectors.toList());

            ctx.status(200);
            ctx.result(createSuccessResponse(result));
        } catch (Exception e) {
            logger.error("获取规则列表失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "获取规则列表失败: " + e.getMessage()));
        }
    }

    /**
     * GET /api/alarm-rules/{id}
     */
    public void getAlarmRule(Context ctx) {
        try {
            String ruleId = ctx.pathParam("id");
            AlarmRule rule = alarmRuleService.getAlarmRule(ruleId);
            if (rule == null) {
                ctx.status(404);
                ctx.result(createErrorResponse(404, "规则不存在"));
                return;
            }
            ctx.status(200);
            ctx.result(createSuccessResponse(ruleToResponseMap(rule)));
        } catch (Exception e) {
            logger.error("获取规则详情失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "获取规则详情失败: " + e.getMessage()));
        }
    }

    /**
     * POST /api/alarm-rules
     */
    public void createAlarmRule(Context ctx) {
        try {
            Map<String, Object> body = objectMapper.readValue(ctx.body(), Map.class);
            AlarmRule rule = buildRuleFromBody(body);

            AlarmRule created = alarmRuleService.createAlarmRule(rule);
            if (created == null) {
                ctx.status(500);
                ctx.result(createErrorResponse(500, "创建规则失败"));
                return;
            }
            ctx.status(201);
            ctx.result(createSuccessResponse(ruleToResponseMap(created)));
        } catch (Exception e) {
            logger.error("创建规则失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "创建规则失败: " + e.getMessage()));
        }
    }

    /**
     * PUT /api/alarm-rules/{id}
     */
    public void updateAlarmRule(Context ctx) {
        try {
            String ruleId = ctx.pathParam("id");
            Map<String, Object> body = objectMapper.readValue(ctx.body(), Map.class);
            AlarmRule rule = buildRuleFromBody(body);

            AlarmRule updated = alarmRuleService.updateAlarmRule(ruleId, rule);
            if (updated == null) {
                ctx.status(404);
                ctx.result(createErrorResponse(404, "规则不存在"));
                return;
            }
            ctx.status(200);
            ctx.result(createSuccessResponse(ruleToResponseMap(updated)));
        } catch (Exception e) {
            logger.error("更新规则失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "更新规则失败: " + e.getMessage()));
        }
    }

    /**
     * DELETE /api/alarm-rules/{id}
     */
    public void deleteAlarmRule(Context ctx) {
        try {
            String ruleId = ctx.pathParam("id");
            boolean deleted = alarmRuleService.deleteAlarmRule(ruleId);
            if (!deleted) {
                ctx.status(404);
                ctx.result(createErrorResponse(404, "规则不存在"));
                return;
            }
            ctx.status(200);
            ctx.result(createSuccessResponse(Map.of("message", "删除成功")));
        } catch (Exception e) {
            logger.error("删除规则失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "删除规则失败: " + e.getMessage()));
        }
    }

    /**
     * PUT /api/alarm-rules/{id}/toggle
     */
    public void toggleRule(Context ctx) {
        try {
            String ruleId = ctx.pathParam("id");
            Map<String, Object> body = objectMapper.readValue(ctx.body(), Map.class);
            boolean enabled = body.get("enabled") != null ? (Boolean) body.get("enabled") : true;

            AlarmRule rule = alarmRuleService.toggleRule(ruleId, enabled);
            if (rule == null) {
                ctx.status(404);
                ctx.result(createErrorResponse(404, "规则不存在"));
                return;
            }
            ctx.status(200);
            ctx.result(createSuccessResponse(ruleToResponseMap(rule)));
        } catch (Exception e) {
            logger.error("切换规则状态失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "切换规则状态失败: " + e.getMessage()));
        }
    }

    /**
     * GET /api/devices/{deviceId}/alarm-rules
     */
    public void getDeviceRules(Context ctx) {
        try {
            String deviceId = ctx.pathParam("deviceId");
            List<AlarmRule> rules = alarmRuleService.getDeviceRules(deviceId);
            List<Map<String, Object>> result = rules.stream()
                .map(this::ruleToResponseMap)
                .collect(java.util.stream.Collectors.toList());

            ctx.status(200);
            ctx.result(createSuccessResponse(result));
        } catch (Exception e) {
            logger.error("获取设备规则列表失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "获取设备规则列表失败: " + e.getMessage()));
        }
    }

    /**
     * GET /api/assemblies/{assemblyId}/alarm-rules
     */
    public void getAssemblyRules(Context ctx) {
        try {
            String assemblyId = ctx.pathParam("assemblyId");
            List<AlarmRule> rules = alarmRuleService.getAssemblyRules(assemblyId);
            List<Map<String, Object>> result = rules.stream()
                .map(this::ruleToResponseMap)
                .collect(java.util.stream.Collectors.toList());

            ctx.status(200);
            ctx.result(createSuccessResponse(result));
        } catch (Exception e) {
            logger.error("获取装置规则列表失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "获取装置规则列表失败: " + e.getMessage()));
        }
    }

    private AlarmRule buildRuleFromBody(Map<String, Object> body) throws Exception {
        AlarmRule rule = new AlarmRule();
        rule.setName((String) body.get("name"));
        rule.setAlarmType((String) body.get("alarmType"));
        rule.setScope((String) body.get("scope"));
        rule.setDeviceId((String) body.get("deviceId"));
        rule.setAssemblyId((String) body.get("assemblyId"));
        rule.setEnabled(body.get("enabled") != null ? (Boolean) body.get("enabled") : true);
        rule.setPriority(body.get("priority") != null ? ((Number) body.get("priority")).intValue() : 0);
        rule.setFlowId((String) body.get("flowId"));

        Object eventKeys = body.get("eventKeys");
        if (eventKeys instanceof List) {
            rule.setEventKeys(objectMapper.writeValueAsString(eventKeys));
        } else if (eventKeys instanceof String) {
            rule.setEventKeys((String) eventKeys);
        }

        Object actions = body.get("actions");
        rule.setActions(objectMapper.writeValueAsString(actions != null ? actions : new HashMap<>()));
        rule.setConditions(body.get("conditions") != null ? objectMapper.writeValueAsString(body.get("conditions")) : null);

        return rule;
    }

    /**
     * 将规则转为 API 响应 Map，对 JSON 字段做解析避免双重编码
     */
    private Map<String, Object> ruleToResponseMap(AlarmRule rule) {
        Map<String, Object> map = rule.toMap();

        // eventKeys: 逐层剥壳直到拿到 List
        try {
            String raw = rule.getEventKeys();
            if (raw != null && !raw.isEmpty() && !"null".equals(raw.trim())) {
                Object cur = objectMapper.readValue(raw, Object.class);
                // 持续剥壳：如果解析结果仍是 String，继续 readValue
                int maxDepth = 10;
                while (cur instanceof String && maxDepth-- > 0) {
                    cur = objectMapper.readValue((String) cur, Object.class);
                }
                if (cur instanceof List) {
                    map.put("eventKeys", cur);
                }
            }
        } catch (Exception e) {
            logger.debug("解析 eventKeys 失败: ruleId={}, raw={}", rule.getRuleId(), rule.getEventKeys());
        }

        // conditions: 逐层剥壳直到拿到 Map
        try {
            String raw = rule.getConditions();
            if (raw != null && !raw.isEmpty() && !"null".equals(raw.trim())) {
                Object cur = objectMapper.readValue(raw, Object.class);
                int maxDepth = 10;
                while (cur instanceof String && maxDepth-- > 0) {
                    cur = objectMapper.readValue((String) cur, Object.class);
                }
                if (cur instanceof Map) {
                    map.put("conditions", cur);
                }
            }
        } catch (Exception e) {
            logger.debug("解析 conditions 失败: ruleId={}", rule.getRuleId());
        }

        // actions: 逐层剥壳直到拿到 Map
        try {
            String raw = rule.getActions();
            if (raw != null && !raw.isEmpty() && !"null".equals(raw.trim())) {
                Object cur = objectMapper.readValue(raw, Object.class);
                int maxDepth = 10;
                while (cur instanceof String && maxDepth-- > 0) {
                    cur = objectMapper.readValue((String) cur, Object.class);
                }
                if (cur instanceof Map) {
                    map.put("actions", cur);
                }
            }
        } catch (Exception e) {
            logger.debug("解析 actions 失败: ruleId={}", rule.getRuleId());
        }

        map.remove("eventTypeIds");
        return map;
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
