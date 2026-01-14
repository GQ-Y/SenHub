package com.digital.video.gateway.database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

/**
 * 雷达设备实体类
 */
public class RadarDevice {
    private int id;
    private String deviceId; // 与devices表关联
    private String radarIp; // 雷达IP地址
    private String radarName;
    private String assemblyId; // 所属装置ID
    private int status; // 0:离线, 1:在线, 2:采集背景中
    private String currentBackgroundId; // 当前使用的背景模型ID
    private String coordinateTransform; // JSON格式的坐标系转换参数
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getRadarIp() {
        return radarIp;
    }

    public void setRadarIp(String radarIp) {
        this.radarIp = radarIp;
    }

    public String getRadarName() {
        return radarName;
    }

    public void setRadarName(String radarName) {
        this.radarName = radarName;
    }

    public String getAssemblyId() {
        return assemblyId;
    }

    public void setAssemblyId(String assemblyId) {
        this.assemblyId = assemblyId;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getCurrentBackgroundId() {
        return currentBackgroundId;
    }

    public void setCurrentBackgroundId(String currentBackgroundId) {
        this.currentBackgroundId = currentBackgroundId;
    }

    public String getCoordinateTransform() {
        return coordinateTransform;
    }

    public void setCoordinateTransform(String coordinateTransform) {
        this.coordinateTransform = coordinateTransform;
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
    public static RadarDevice fromResultSet(ResultSet rs) throws SQLException {
        RadarDevice device = new RadarDevice();
        device.setId(rs.getInt("id"));
        device.setDeviceId(rs.getString("device_id"));
        device.setRadarIp(rs.getString("radar_ip"));
        device.setRadarName(rs.getString("radar_name"));
        device.setAssemblyId(rs.getString("assembly_id"));
        device.setStatus(rs.getInt("status"));
        device.setCurrentBackgroundId(rs.getString("current_background_id"));
        device.setCoordinateTransform(rs.getString("coordinate_transform"));
        device.setCreatedAt(rs.getTimestamp("created_at"));
        device.setUpdatedAt(rs.getTimestamp("updated_at"));
        return device;
    }

    /**
     * 转换为Map（用于JSON响应）
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("deviceId", deviceId);
        map.put("radarIp", radarIp);
        map.put("radarName", radarName);
        map.put("assemblyId", assemblyId);
        map.put("status", status);
        map.put("currentBackgroundId", currentBackgroundId);
        map.put("coordinateTransform", coordinateTransform);
        if (createdAt != null) map.put("createdAt", createdAt.toString());
        if (updatedAt != null) map.put("updatedAt", updatedAt.toString());
        return map;
    }
}
