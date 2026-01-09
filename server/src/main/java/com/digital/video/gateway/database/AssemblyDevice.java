package com.digital.video.gateway.database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

/**
 * 装置设备关联实体类
 */
public class AssemblyDevice {
    private int id;
    private String assemblyId;
    private String deviceId;
    private String deviceRole; // left_camera, right_camera, top_camera, speaker, radar, other
    private String positionInfo; // JSON string
    private int priority;
    private boolean enabled;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getAssemblyId() { return assemblyId; }
    public void setAssemblyId(String assemblyId) { this.assemblyId = assemblyId; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getDeviceRole() { return deviceRole; }
    public void setDeviceRole(String deviceRole) { this.deviceRole = deviceRole; }

    public String getPositionInfo() { return positionInfo; }
    public void setPositionInfo(String positionInfo) { this.positionInfo = positionInfo; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    /**
     * 从ResultSet构建对象
     */
    public static AssemblyDevice fromResultSet(ResultSet rs) throws SQLException {
        AssemblyDevice ad = new AssemblyDevice();
        ad.setId(rs.getInt("id"));
        ad.setAssemblyId(rs.getString("assembly_id"));
        ad.setDeviceId(rs.getString("device_id"));
        ad.setDeviceRole(rs.getString("device_role"));
        ad.setPositionInfo(rs.getString("position_info"));
        ad.setPriority(rs.getInt("priority"));
        ad.setEnabled(rs.getInt("enabled") == 1);
        ad.setCreatedAt(rs.getTimestamp("created_at"));
        ad.setUpdatedAt(rs.getTimestamp("updated_at"));
        return ad;
    }

    /**
     * 转换为Map（用于JSON响应）
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", String.valueOf(id));
        map.put("assemblyId", assemblyId);
        map.put("deviceId", deviceId);
        map.put("deviceRole", deviceRole);
        map.put("positionInfo", positionInfo);
        map.put("priority", priority);
        map.put("enabled", enabled);
        if (createdAt != null) map.put("createdAt", createdAt.toString());
        if (updatedAt != null) map.put("updatedAt", updatedAt.toString());
        return map;
    }
}
