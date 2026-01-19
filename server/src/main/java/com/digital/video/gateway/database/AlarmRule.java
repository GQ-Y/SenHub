package com.digital.video.gateway.database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

/**
 * 报警规则实体类
 */
public class AlarmRule {
    private int id;
    private String ruleId;
    private String name;
    private String alarmType; // helmet_detection, vest_detection, vehicle_alarm, input_port, radar_pointcloud, other
    private String scope; // global, assembly, device
    private String deviceId;
    private String assemblyId;
    private boolean enabled;
    private int priority;
    private String actions; // JSON string
    private String conditions; // JSON string
    private String flowId; // 关联的流程定义
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAlarmType() { return alarmType; }
    public void setAlarmType(String alarmType) { this.alarmType = alarmType; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getAssemblyId() { return assemblyId; }
    public void setAssemblyId(String assemblyId) { this.assemblyId = assemblyId; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public String getActions() { return actions; }
    public void setActions(String actions) { this.actions = actions; }

    public String getConditions() { return conditions; }
    public void setConditions(String conditions) { this.conditions = conditions; }

    public String getFlowId() { return flowId; }
    public void setFlowId(String flowId) { this.flowId = flowId; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    /**
     * 从ResultSet构建对象
     */
    public static AlarmRule fromResultSet(ResultSet rs) throws SQLException {
        AlarmRule rule = new AlarmRule();
        rule.setId(rs.getInt("id"));
        rule.setRuleId(rs.getString("rule_id"));
        rule.setName(rs.getString("name"));
        rule.setAlarmType(rs.getString("alarm_type"));
        rule.setScope(rs.getString("scope"));
        rule.setDeviceId(rs.getString("device_id"));
        rule.setAssemblyId(rs.getString("assembly_id"));
        rule.setEnabled(rs.getInt("enabled") == 1);
        rule.setPriority(rs.getInt("priority"));
        rule.setActions(rs.getString("actions"));
        rule.setConditions(rs.getString("conditions"));
        try {
            rule.setFlowId(rs.getString("flow_id"));
        } catch (SQLException ignore) {
            // 兼容旧表
        }
        rule.setCreatedAt(rs.getTimestamp("created_at"));
        rule.setUpdatedAt(rs.getTimestamp("updated_at"));
        return rule;
    }

    /**
     * 转换为Map（用于JSON响应）
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", String.valueOf(id));
        map.put("ruleId", ruleId);
        map.put("name", name);
        map.put("alarmType", alarmType);
        map.put("scope", scope);
        map.put("deviceId", deviceId);
        map.put("assemblyId", assemblyId);
        map.put("enabled", enabled);
        map.put("priority", priority);
        map.put("actions", actions);
        map.put("conditions", conditions);
        map.put("flowId", flowId);
        if (createdAt != null) map.put("createdAt", createdAt.toString());
        if (updatedAt != null) map.put("updatedAt", updatedAt.toString());
        return map;
    }
}
