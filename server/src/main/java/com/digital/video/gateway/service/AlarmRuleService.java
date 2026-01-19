package com.digital.video.gateway.service;

import com.digital.video.gateway.database.AlarmRule;
import com.digital.video.gateway.database.Database;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 报警规则服务
 */
public class AlarmRuleService {
    private static final Logger logger = LoggerFactory.getLogger(AlarmRuleService.class);
    private final Database database;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AlarmRuleService(Database database) {
        this.database = database;
    }

    /**
     * 获取规则列表
     */
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

    /**
     * 获取规则详情
     */
    public AlarmRule getAlarmRule(String ruleId) {
        String sql = "SELECT * FROM alarm_rules WHERE rule_id = ?";
        Connection conn = database.getConnection(); try (
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
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

    /**
     * 创建规则
     */
    public AlarmRule createAlarmRule(AlarmRule rule) {
        if (rule.getRuleId() == null || rule.getRuleId().isEmpty()) {
            rule.setRuleId(UUID.randomUUID().toString());
        }
        // 兼容新版本：如果没有alarmType但有eventTypeIds，使用默认值
        String alarmType = rule.getAlarmType();
        if (alarmType == null || alarmType.isEmpty()) {
            alarmType = "custom";  // 默认类型
        }
        // 兼容新版本：如果没有actions，使用空JSON对象
        String actions = rule.getActions();
        if (actions == null || actions.isEmpty()) {
            actions = "{}";
        }
        
        String sql = "INSERT INTO alarm_rules (rule_id, name, alarm_type, scope, device_id, assembly_id, enabled, priority, flow_id, event_type_ids, actions, conditions) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        Connection conn = database.getConnection(); try (
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, rule.getRuleId());
            pstmt.setString(2, rule.getName());
            pstmt.setString(3, alarmType);
            pstmt.setString(4, rule.getScope());
            pstmt.setString(5, rule.getDeviceId());
            pstmt.setString(6, rule.getAssemblyId());
            pstmt.setInt(7, rule.isEnabled() ? 1 : 0);
            pstmt.setInt(8, rule.getPriority());
            pstmt.setString(9, rule.getFlowId());
            pstmt.setString(10, rule.getEventTypeIds());
            pstmt.setString(11, actions);
            pstmt.setString(12, rule.getConditions());
            pstmt.executeUpdate();
            return getAlarmRule(rule.getRuleId());
        } catch (SQLException e) {
            logger.error("创建规则失败", e);
            return null;
        }
    }

    /**
     * 更新规则
     */
    public AlarmRule updateAlarmRule(String ruleId, AlarmRule rule) {
        // 兼容新版本：如果没有alarmType但有eventTypeIds，使用默认值
        String alarmType = rule.getAlarmType();
        if (alarmType == null || alarmType.isEmpty()) {
            alarmType = "custom";  // 默认类型
        }
        // 兼容新版本：如果没有actions，使用空JSON对象
        String actions = rule.getActions();
        if (actions == null || actions.isEmpty()) {
            actions = "{}";
        }
        
        String sql = "UPDATE alarm_rules SET name = ?, alarm_type = ?, scope = ?, device_id = ?, assembly_id = ?, enabled = ?, priority = ?, flow_id = ?, event_type_ids = ?, actions = ?, conditions = ?, updated_at = CURRENT_TIMESTAMP WHERE rule_id = ?";
        Connection conn = database.getConnection(); try (
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, rule.getName());
            pstmt.setString(2, alarmType);
            pstmt.setString(3, rule.getScope());
            pstmt.setString(4, rule.getDeviceId());
            pstmt.setString(5, rule.getAssemblyId());
            pstmt.setInt(6, rule.isEnabled() ? 1 : 0);
            pstmt.setInt(7, rule.getPriority());
            pstmt.setString(8, rule.getFlowId());
            pstmt.setString(9, rule.getEventTypeIds());
            pstmt.setString(10, actions);
            pstmt.setString(11, rule.getConditions());
            pstmt.setString(12, ruleId);
            pstmt.executeUpdate();
            return getAlarmRule(ruleId);
        } catch (SQLException e) {
            logger.error("更新规则失败: {}", ruleId, e);
            return null;
        }
    }

    /**
     * 删除规则
     */
    public boolean deleteAlarmRule(String ruleId) {
        String sql = "DELETE FROM alarm_rules WHERE rule_id = ?";
        Connection conn = database.getConnection(); try (
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, ruleId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("删除规则失败: {}", ruleId, e);
            return false;
        }
    }

    /**
     * 启用/禁用规则
     */
    public AlarmRule toggleRule(String ruleId, boolean enabled) {
        String sql = "UPDATE alarm_rules SET enabled = ?, updated_at = CURRENT_TIMESTAMP WHERE rule_id = ?";
        Connection conn = database.getConnection(); try (
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, enabled ? 1 : 0);
            pstmt.setString(2, ruleId);
            pstmt.executeUpdate();
            return getAlarmRule(ruleId);
        } catch (SQLException e) {
            logger.error("切换规则状态失败: {}", ruleId, e);
            return null;
        }
    }

    /**
     * 获取设备的所有规则
     */
    public List<AlarmRule> getDeviceRules(String deviceId) {
        return getAlarmRules(deviceId, null, null, null);
    }

    /**
     * 获取装置的所有规则
     */
    public List<AlarmRule> getAssemblyRules(String assemblyId) {
        return getAlarmRules(null, assemblyId, null, null);
    }

    /**
     * 匹配规则（核心方法）
     * 根据设备ID、装置ID、报警类型和报警数据匹配规则
     */
    public List<AlarmRule> matchRules(String deviceId, String assemblyId, String alarmType,
            Map<String, Object> alarmData) {
        List<AlarmRule> matchedRules = new ArrayList<>();

        // 1. 查询相关规则
        List<AlarmRule> candidateRules = new ArrayList<>();

        // 全局规则
        candidateRules.addAll(getAlarmRules(null, null, alarmType, true));

        // 装置级规则
        if (assemblyId != null) {
            candidateRules.addAll(getAlarmRules(null, assemblyId, alarmType, true));
        }

        // 设备级规则
        candidateRules.addAll(getAlarmRules(deviceId, null, alarmType, true));

        // 2. 过滤enabled=true的规则（已在查询中过滤）
        // 3. 检查conditions条件
        for (AlarmRule rule : candidateRules) {
            if (checkConditions(rule, alarmData)) {
                matchedRules.add(rule);
            }
        }

        // 4. 按priority排序
        matchedRules.sort((r1, r2) -> Integer.compare(r2.getPriority(), r1.getPriority()));

        return matchedRules;
    }

    /**
     * 检查规则条件
     */
    private boolean checkConditions(AlarmRule rule, Map<String, Object> alarmData) {
        if (rule.getConditions() == null || rule.getConditions().isEmpty()) {
            return true; // 无条件，匹配
        }

        try {
            Map<String, Object> conditions = objectMapper.readValue(rule.getConditions(),
                    new TypeReference<Map<String, Object>>() {
                    });

            // 检查区域限制
            if (conditions.containsKey("area")) {
                String area = (String) conditions.get("area");
                if (!"all".equals(area) && alarmData != null) {
                    String alarmArea = (String) alarmData.get("area");
                    if (alarmArea == null || !area.equals(alarmArea)) {
                        return false;
                    }
                }
            }

            // 检查距离范围（雷达点云）
            if (conditions.containsKey("distanceRange") && alarmData != null) {
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
