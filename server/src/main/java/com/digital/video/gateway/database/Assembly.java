package com.digital.video.gateway.database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

/**
 * 装置实体类
 */
public class Assembly {
    private int id;
    private String assemblyId;
    private String name;
    private String description;
    private String location;
    private int status; // 0: 禁用, 1: 启用
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getAssemblyId() {
        return assemblyId;
    }

    public void setAssemblyId(String assemblyId) {
        this.assemblyId = assemblyId;
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

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
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
    public static Assembly fromResultSet(ResultSet rs) throws SQLException {
        Assembly assembly = new Assembly();
        assembly.setId(rs.getInt("id"));
        assembly.setAssemblyId(rs.getString("assembly_id"));
        assembly.setName(rs.getString("name"));
        assembly.setDescription(rs.getString("description"));
        assembly.setLocation(rs.getString("location"));
        assembly.setStatus(rs.getInt("status"));
        assembly.setCreatedAt(rs.getTimestamp("created_at"));
        assembly.setUpdatedAt(rs.getTimestamp("updated_at"));
        return assembly;
    }

    /**
     * 转换为Map（用于JSON响应）
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", String.valueOf(id));
        map.put("assemblyId", assemblyId);
        map.put("name", name);
        map.put("description", description);
        map.put("location", location);
        map.put("status", status);
        if (createdAt != null)
            map.put("createdAt", createdAt.toString());
        if (updatedAt != null)
            map.put("updatedAt", updatedAt.toString());
        return map;
    }
}
