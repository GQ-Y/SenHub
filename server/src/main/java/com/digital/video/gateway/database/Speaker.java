package com.digital.video.gateway.database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

/**
 * 音柱设备实体类
 */
public class Speaker {
    private int id;
    private String deviceId;
    private String name;
    private String apiEndpoint;
    private String apiType; // http, mqtt, tcp
    private String apiConfig; // JSON string
    private String status; // online, offline
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getApiEndpoint() { return apiEndpoint; }
    public void setApiEndpoint(String apiEndpoint) { this.apiEndpoint = apiEndpoint; }

    public String getApiType() { return apiType; }
    public void setApiType(String apiType) { this.apiType = apiType; }

    public String getApiConfig() { return apiConfig; }
    public void setApiConfig(String apiConfig) { this.apiConfig = apiConfig; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    /**
     * 从ResultSet构建对象
     */
    public static Speaker fromResultSet(ResultSet rs) throws SQLException {
        Speaker speaker = new Speaker();
        speaker.setId(rs.getInt("id"));
        speaker.setDeviceId(rs.getString("device_id"));
        speaker.setName(rs.getString("name"));
        speaker.setApiEndpoint(rs.getString("api_endpoint"));
        speaker.setApiType(rs.getString("api_type"));
        speaker.setApiConfig(rs.getString("api_config"));
        speaker.setStatus(rs.getString("status"));
        speaker.setCreatedAt(rs.getTimestamp("created_at"));
        speaker.setUpdatedAt(rs.getTimestamp("updated_at"));
        return speaker;
    }

    /**
     * 转换为Map（用于JSON响应）
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", String.valueOf(id));
        map.put("deviceId", deviceId);
        map.put("name", name);
        map.put("apiEndpoint", apiEndpoint);
        map.put("apiType", apiType);
        map.put("apiConfig", apiConfig);
        map.put("status", status);
        if (createdAt != null) map.put("createdAt", createdAt.toString());
        if (updatedAt != null) map.put("updatedAt", updatedAt.toString());
        return map;
    }
}
