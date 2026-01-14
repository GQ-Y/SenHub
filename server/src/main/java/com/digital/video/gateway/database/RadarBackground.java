package com.digital.video.gateway.database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

/**
 * 雷达背景模型实体类
 */
public class RadarBackground {
    private int id;
    private String backgroundId;
    private String deviceId;
    private String assemblyId;
    private int frameCount;
    private int pointCount;
    private float gridResolution; // 体素分辨率（米）
    private int durationSeconds; // 采集时长（秒）
    private String filePath; // .pcd文件路径（可选）
    private String status; // collecting, ready, expired
    private Timestamp createdAt;

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getBackgroundId() { return backgroundId; }
    public void setBackgroundId(String backgroundId) { this.backgroundId = backgroundId; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getAssemblyId() { return assemblyId; }
    public void setAssemblyId(String assemblyId) { this.assemblyId = assemblyId; }

    public int getFrameCount() { return frameCount; }
    public void setFrameCount(int frameCount) { this.frameCount = frameCount; }

    public int getPointCount() { return pointCount; }
    public void setPointCount(int pointCount) { this.pointCount = pointCount; }

    public float getGridResolution() { return gridResolution; }
    public void setGridResolution(float gridResolution) { this.gridResolution = gridResolution; }

    public int getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(int durationSeconds) { this.durationSeconds = durationSeconds; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public static RadarBackground fromResultSet(ResultSet rs) throws SQLException {
        RadarBackground bg = new RadarBackground();
        bg.setId(rs.getInt("id"));
        bg.setBackgroundId(rs.getString("background_id"));
        bg.setDeviceId(rs.getString("device_id"));
        bg.setAssemblyId(rs.getString("assembly_id"));
        bg.setFrameCount(rs.getInt("frame_count"));
        bg.setPointCount(rs.getInt("point_count"));
        bg.setGridResolution(rs.getFloat("grid_resolution"));
        bg.setDurationSeconds(rs.getInt("duration_seconds"));
        bg.setFilePath(rs.getString("file_path"));
        bg.setStatus(rs.getString("status"));
        bg.setCreatedAt(rs.getTimestamp("created_at"));
        return bg;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("backgroundId", backgroundId);
        map.put("deviceId", deviceId);
        map.put("assemblyId", assemblyId);
        map.put("frameCount", frameCount);
        map.put("pointCount", pointCount);
        map.put("gridResolution", gridResolution);
        map.put("durationSeconds", durationSeconds);
        map.put("filePath", filePath);
        map.put("status", status);
        if (createdAt != null) map.put("createdAt", createdAt.toString());
        return map;
    }
}
