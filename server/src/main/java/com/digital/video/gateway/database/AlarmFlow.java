package com.digital.video.gateway.database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

/**
 * 报警流程定义实体
 */
public class AlarmFlow {
    private int id;
    private String flowId;
    private String name;
    private String description;
    private String flowType;
    private String nodes;        // JSON字符串
    private String connections;  // JSON字符串
    private boolean isDefault;
    private boolean enabled;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getFlowId() {
        return flowId;
    }

    public void setFlowId(String flowId) {
        this.flowId = flowId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getFlowType() {
        return flowType;
    }

    public void setFlowType(String flowType) {
        this.flowType = flowType;
    }

    public String getNodes() {
        return nodes;
    }

    public void setNodes(String nodes) {
        this.nodes = nodes;
    }

    public String getConnections() {
        return connections;
    }

    public void setConnections(String connections) {
        this.connections = connections;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * 从ResultSet构建对象
     */
    public static AlarmFlow fromResultSet(ResultSet rs) throws SQLException {
        AlarmFlow flow = new AlarmFlow();
        flow.setId(rs.getInt("id"));
        flow.setFlowId(rs.getString("flow_id"));
        flow.setName(rs.getString("name"));
        flow.setDescription(rs.getString("description"));
        flow.setFlowType(rs.getString("flow_type"));
        flow.setNodes(rs.getString("nodes"));
        flow.setConnections(rs.getString("connections"));
        flow.setDefault(rs.getInt("is_default") == 1);
        flow.setEnabled(rs.getInt("enabled") == 1);
        flow.setCreatedAt(rs.getTimestamp("created_at"));
        flow.setUpdatedAt(rs.getTimestamp("updated_at"));
        return flow;
    }

    /**
     * 转Map用于JSON响应
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("flowId", flowId);
        map.put("name", name);
        map.put("description", description);
        map.put("flowType", flowType);
        map.put("nodes", nodes);
        map.put("connections", connections);
        map.put("isDefault", isDefault);
        map.put("enabled", enabled);
        if (createdAt != null) {
            map.put("createdAt", createdAt.toString());
        }
        if (updatedAt != null) {
            map.put("updatedAt", updatedAt.toString());
        }
        return map;
    }
}
