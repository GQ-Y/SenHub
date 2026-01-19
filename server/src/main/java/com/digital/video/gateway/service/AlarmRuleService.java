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
     * 
     * 匹配逻辑：
     * 1. 全局规则（scope=global）：适用于所有设备
     * 2. 装置规则（scope=assembly）：适用于指定装置的设备
     * 3. 设备规则（scope=device）：适用于指定设备
     */
    public List<AlarmRule> matchRules(String deviceId, String assemblyId, String alarmType,
            Map<String, Object> alarmData) {
        List<AlarmRule> matchedRules = new ArrayList<>();
        
        logger.debug("开始匹配规则: deviceId={}, assemblyId={}, alarmType={}", deviceId, assemblyId, alarmType);

        // 1. 查询所有启用的规则
        List<AlarmRule> allRules = getAlarmRules(null, null, null, true);
        logger.debug("查询到 {} 条启用的规则", allRules.size());
        
        // 2. 筛选符合范围的规则
        for (AlarmRule rule : allRules) {
            String scope = rule.getScope();
            boolean scopeMatch = false;
            
            if ("global".equals(scope)) {
                // 全局规则适用于所有设备
                scopeMatch = true;
            } else if ("assembly".equals(scope) && assemblyId != null) {
                // 装置规则：检查装置ID
                scopeMatch = assemblyId.equals(rule.getAssemblyId());
            } else if ("device".equals(scope) && deviceId != null) {
                // 设备规则：检查设备ID
                scopeMatch = deviceId.equals(rule.getDeviceId());
            }
            
            if (!scopeMatch) {
                logger.debug("规则 {} 范围不匹配: scope={}, ruleDeviceId={}, targetDeviceId={}", 
                        rule.getName(), scope, rule.getDeviceId(), deviceId);
                continue;
            }
            
            // 3. 检查conditions条件
            if (!checkConditions(rule, alarmData)) {
                logger.debug("规则 {} 条件不匹配", rule.getName());
                continue;
            }
            
            // 规则匹配成功
            logger.info("规则匹配成功: {} (scope={}, flowId={})", rule.getName(), scope, rule.getFlowId());
            matchedRules.add(rule);
        }

        // 4. 按 scope优先级（设备>装置>全局）和 priority 排序
        // scope优先级：device=0, assembly=1, global=2
        matchedRules.sort((r1, r2) -> {
            int scopeOrder1 = getScopePriority(r1.getScope());
            int scopeOrder2 = getScopePriority(r2.getScope());
            if (scopeOrder1 != scopeOrder2) {
                return Integer.compare(scopeOrder1, scopeOrder2); // scope越小优先级越高
            }
            return Integer.compare(r2.getPriority(), r1.getPriority()); // priority越大优先级越高
        });
        
        logger.debug("共匹配到 {} 条规则", matchedRules.size());

        return matchedRules;
    }
    
    /**
     * 获取scope的优先级顺序
     * 设备级规则优先于装置级，装置级优先于全局
     */
    private int getScopePriority(String scope) {
        if ("device".equals(scope)) return 0;   // 最高优先级
        if ("assembly".equals(scope)) return 1; // 中等优先级
        return 2; // global 最低优先级
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
