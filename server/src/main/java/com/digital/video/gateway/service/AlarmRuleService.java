package com.digital.video.gateway.service;

import com.digital.video.gateway.database.AlarmRule;
import com.digital.video.gateway.database.CanonicalEventTable;
import com.digital.video.gateway.database.Database;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * 报警规则服务
 * 规则匹配统一基于 eventKeys（canonical_events 标准事件键）
 */
public class AlarmRuleService {
    private static final Logger logger = LoggerFactory.getLogger(AlarmRuleService.class);
    private final Database database;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AlarmRuleService(Database database) {
        this.database = database;
    }

    public List<AlarmRule> getAlarmRules(String deviceId, String assemblyId, String alarmType, Boolean enabled) {
        List<AlarmRule> rules = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM alarm_rules WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (deviceId != null && !deviceId.isEmpty()) {
            sql.append(" AND device_id = ?");
            params.add(deviceId);
        }
        if (assemblyId != null && !assemblyId.isEmpty()) {
            sql.append(" AND assembly_id = ?");
            params.add(assemblyId);
        }
        if (alarmType != null && !alarmType.isEmpty()) {
            sql.append(" AND alarm_type = ?");
            params.add(alarmType);
        }
        if (enabled != null) {
            sql.append(" AND enabled = ?");
            params.add(enabled ? 1 : 0);
        }
        sql.append(" ORDER BY priority DESC, created_at ASC");

        Connection conn = database.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object param = params.get(i);
                if (param instanceof String) {
                    pstmt.setString(i + 1, (String) param);
                } else if (param instanceof Integer) {
                    pstmt.setInt(i + 1, (Integer) param);
                }
            }
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                rules.add(AlarmRule.fromResultSet(rs));
            }
        } catch (SQLException e) {
            logger.error("获取规则列表失败", e);
        }
        return rules;
    }

    public AlarmRule getAlarmRule(String ruleId) {
        String sql = "SELECT * FROM alarm_rules WHERE rule_id = ?";
        Connection conn = database.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, ruleId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return AlarmRule.fromResultSet(rs);
            }
        } catch (SQLException e) {
            logger.error("获取规则详情失败: {}", ruleId, e);
        }
        return null;
    }

    public AlarmRule createAlarmRule(AlarmRule rule) {
        if (rule.getRuleId() == null || rule.getRuleId().isEmpty()) {
            rule.setRuleId(UUID.randomUUID().toString());
        }
        String alarmType = rule.getAlarmType();
        if (alarmType == null || alarmType.isEmpty()) {
            alarmType = "custom";
        }
        String actions = rule.getActions();
        if (actions == null || actions.isEmpty()) {
            actions = "{}";
        }

        String sql = "INSERT INTO alarm_rules (rule_id, name, alarm_type, scope, device_id, assembly_id, enabled, priority, flow_id, event_type_ids, event_keys, actions, conditions) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        Connection conn = database.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, rule.getRuleId());
            pstmt.setString(2, rule.getName());
            pstmt.setString(3, alarmType);
            pstmt.setString(4, rule.getScope());
            pstmt.setString(5, rule.getDeviceId());
            pstmt.setString(6, rule.getAssemblyId());
            pstmt.setInt(7, rule.isEnabled() ? 1 : 0);
            pstmt.setInt(8, rule.getPriority());
            pstmt.setString(9, rule.getFlowId());
            pstmt.setString(10, null); // event_type_ids 不再使用
            pstmt.setString(11, rule.getEventKeys());
            pstmt.setString(12, actions);
            pstmt.setString(13, rule.getConditions());
            pstmt.executeUpdate();
            return getAlarmRule(rule.getRuleId());
        } catch (SQLException e) {
            logger.error("创建规则失败", e);
            return null;
        }
    }

    public AlarmRule updateAlarmRule(String ruleId, AlarmRule rule) {
        AlarmRule existing = getAlarmRule(ruleId);
        if (existing != null && rule.getEventKeys() == null) {
            rule.setEventKeys(existing.getEventKeys());
        }
        String alarmType = rule.getAlarmType();
        if (alarmType == null || alarmType.isEmpty()) {
            alarmType = "custom";
        }
        String actions = rule.getActions();
        if (actions == null || actions.isEmpty()) {
            actions = "{}";
        }

        String sql = "UPDATE alarm_rules SET name = ?, alarm_type = ?, scope = ?, device_id = ?, assembly_id = ?, enabled = ?, priority = ?, flow_id = ?, event_type_ids = ?, event_keys = ?, actions = ?, conditions = ?, updated_at = CURRENT_TIMESTAMP WHERE rule_id = ?";
        Connection conn = database.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, rule.getName());
            pstmt.setString(2, alarmType);
            pstmt.setString(3, rule.getScope());
            pstmt.setString(4, rule.getDeviceId());
            pstmt.setString(5, rule.getAssemblyId());
            pstmt.setInt(6, rule.isEnabled() ? 1 : 0);
            pstmt.setInt(7, rule.getPriority());
            pstmt.setString(8, rule.getFlowId());
            pstmt.setString(9, null); // event_type_ids 不再使用
            pstmt.setString(10, rule.getEventKeys());
            pstmt.setString(11, actions);
            pstmt.setString(12, rule.getConditions());
            pstmt.setString(13, ruleId);
            pstmt.executeUpdate();
            return getAlarmRule(ruleId);
        } catch (SQLException e) {
            logger.error("更新规则失败: {}", ruleId, e);
            return null;
        }
    }

    public boolean deleteAlarmRule(String ruleId) {
        String sql = "DELETE FROM alarm_rules WHERE rule_id = ?";
        Connection conn = database.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, ruleId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("删除规则失败: {}", ruleId, e);
            return false;
        }
    }

    public AlarmRule toggleRule(String ruleId, boolean enabled) {
        String sql = "UPDATE alarm_rules SET enabled = ?, updated_at = CURRENT_TIMESTAMP WHERE rule_id = ?";
        Connection conn = database.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, enabled ? 1 : 0);
            pstmt.setString(2, ruleId);
            pstmt.executeUpdate();
            return getAlarmRule(ruleId);
        } catch (SQLException e) {
            logger.error("切换规则状态失败: {}", ruleId, e);
            return null;
        }
    }

    public List<AlarmRule> getDeviceRules(String deviceId) {
        return getAlarmRules(deviceId, null, null, null);
    }

    public List<AlarmRule> getAssemblyRules(String assemblyId) {
        return getAlarmRules(null, assemblyId, null, null);
    }

    /**
     * 匹配规则（核心方法）
     * 优先级：设备 > 装置 > 全局
     */
    public List<AlarmRule> matchRules(String deviceId, String assemblyId, String alarmType,
            Map<String, Object> alarmData) {
        return matchRules(deviceId, assemblyId, alarmType, alarmData, null);
    }

    public List<AlarmRule> matchRules(String deviceId, String assemblyId, String alarmType,
            Map<String, Object> alarmData, String alarmTypeDisplay) {
        List<AlarmRule> matchedRules = new ArrayList<>();

        if (alarmTypeDisplay == null || alarmTypeDisplay.isEmpty()) {
            alarmTypeDisplay = alarmType;
            try {
                Connection conn = database.getConnection();
                Map<String, Object> event = CanonicalEventTable.getCanonicalEvent(conn, alarmType);
                if (event != null) {
                    String nameZh = (String) event.get("nameZh");
                    if (nameZh != null && !nameZh.equals(alarmType)) {
                        alarmTypeDisplay = nameZh + "(" + alarmType + ")";
                    }
                }
            } catch (Exception e) {
                // ignore
            }
        }

        logger.debug("开始匹配规则: deviceId={}, assemblyId={}, alarmType={}", deviceId, assemblyId, alarmTypeDisplay);

        List<AlarmRule> allRules = getAlarmRules(null, null, null, true);
        logger.debug("查询到 {} 条启用的规则", allRules.size());

        for (AlarmRule rule : allRules) {
            String scope = rule.getScope();
            boolean scopeMatch = false;

            if ("global".equals(scope)) {
                scopeMatch = true;
            } else if ("assembly".equals(scope) && assemblyId != null) {
                scopeMatch = assemblyId.equals(rule.getAssemblyId());
            } else if ("device".equals(scope) && deviceId != null) {
                scopeMatch = deviceId.equals(rule.getDeviceId());
            }

            if (!scopeMatch) {
                logger.debug("规则 {} 范围不匹配: scope={}, ruleDeviceId={}, targetDeviceId={}",
                        rule.getName(), scope, rule.getDeviceId(), deviceId);
                continue;
            }

            boolean eventMatched = checkEventMatch(rule, alarmType);
            if (!eventMatched) {
                logger.info("规则 {} 事件类型不匹配: eventKeys={}, alarmType={}",
                        rule.getName(), rule.getEventKeys(), alarmTypeDisplay);
                continue;
            }

            if (!checkConditions(rule, alarmData)) {
                logger.debug("规则 {} 条件不匹配", rule.getName());
                continue;
            }

            logger.info("规则匹配成功: {} (scope={}, flowId={})", rule.getName(), scope, rule.getFlowId());
            matchedRules.add(rule);
        }

        matchedRules.sort((r1, r2) -> {
            int scopeOrder1 = getScopePriority(r1.getScope());
            int scopeOrder2 = getScopePriority(r2.getScope());
            if (scopeOrder1 != scopeOrder2) {
                return Integer.compare(scopeOrder1, scopeOrder2);
            }
            return Integer.compare(r2.getPriority(), r1.getPriority());
        });

        logger.debug("共匹配到 {} 条规则", matchedRules.size());
        return matchedRules;
    }

    private int getScopePriority(String scope) {
        if ("device".equals(scope)) return 0;
        if ("assembly".equals(scope)) return 1;
        return 2;
    }

    /**
     * 检查事件类型是否匹配
     * 仅使用 eventKeys（canonical_events 标准事件键）进行匹配
     */
    private boolean checkEventMatch(AlarmRule rule, String alarmType) {
        if (alarmType == null || alarmType.isEmpty()) {
            return false;
        }

        List<String> ruleEventKeys = parseEventKeys(rule.getEventKeys());

        if (ruleEventKeys == null || ruleEventKeys.isEmpty()) {
            logger.debug("规则 {} 未配置事件类型，不匹配: alarmType={}", rule.getName(), alarmType);
            return false;
        }

        boolean matched = ruleEventKeys.contains(alarmType);
        if (matched) {
            logger.info("eventKeys匹配: eventKey={}, ruleEventKeys={}", alarmType, ruleEventKeys);
        }
        return matched;
    }

    /**
     * 解析 eventKeys JSON 字符串为 List，兼容任意层数的双重/多重编码
     */
    @SuppressWarnings("unchecked")
    private List<String> parseEventKeys(String eventKeysStr) {
        if (eventKeysStr == null || eventKeysStr.isEmpty() || "null".equals(eventKeysStr.trim())) {
            return null;
        }
        try {
            Object cur = objectMapper.readValue(eventKeysStr, Object.class);
            int maxDepth = 10;
            while (cur instanceof String && maxDepth-- > 0) {
                cur = objectMapper.readValue((String) cur, Object.class);
            }
            if (cur instanceof List) {
                List<String> list = (List<String>) cur;
                return list.isEmpty() ? null : list;
            }
        } catch (Exception e) {
            logger.debug("解析eventKeys失败: {}", eventKeysStr, e);
        }
        return null;
    }

    private boolean checkConditions(AlarmRule rule, Map<String, Object> alarmData) {
        if (rule.getConditions() == null || rule.getConditions().isEmpty()) {
            return true;
        }

        try {
            String conditionsStr = rule.getConditions();
            while (conditionsStr != null && conditionsStr.startsWith("\"") && conditionsStr.endsWith("\"")) {
                try {
                    String parsed = objectMapper.readValue(conditionsStr, String.class);
                    if (parsed.equals(conditionsStr)) break;
                    conditionsStr = parsed;
                } catch (Exception e) {
                    break;
                }
            }

            Map<String, Object> conditions = objectMapper.readValue(conditionsStr,
                    new TypeReference<Map<String, Object>>() {});

            if (conditions.containsKey("area")) {
                String area = (String) conditions.get("area");
                if (!"all".equals(area) && alarmData != null) {
                    String alarmArea = (String) alarmData.get("area");
                    if (alarmArea == null || !area.equals(alarmArea)) {
                        return false;
                    }
                }
            }

            if (conditions.containsKey("distanceRange") && alarmData != null) {
                @SuppressWarnings("unchecked")
                List<Number> distanceRange = (List<Number>) conditions.get("distanceRange");
                if (distanceRange != null && distanceRange.size() == 2) {
                    Number distance = (Number) alarmData.get("distance");
                    if (distance != null) {
                        double dist = distance.doubleValue();
                        double minDist = distanceRange.get(0).doubleValue();
                        double maxDist = distanceRange.get(1).doubleValue();
                        if (dist < minDist || dist > maxDist) {
                            return false;
                        }
                    }
                }
            }

            return true;
        } catch (Exception e) {
            logger.error("检查规则条件失败: ruleId={}", rule.getRuleId(), e);
            return false;
        }
    }
}
