package com.digital.video.gateway.database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

/**
 * 雷达防区配置实体类
 */
public class RadarDefenseZone {
    private int id;
    private String zoneId;
    private String deviceId;
    private String assemblyId;
    private String backgroundId;
    private String zoneType; // 'shrink' 或 'bounding_box'
    private Integer shrinkDistanceCm;
    private Float minX, maxX, minY, maxY, minZ, maxZ;
    private String cameraDeviceId;
    private Integer cameraChannel;
    private String coordinateTransform;
    private boolean enabled;
    private String name;
    private String description;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getZoneId() { return zoneId; }
    public void setZoneId(String zoneId) { this.zoneId = zoneId; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getAssemblyId() { return assemblyId; }
    public void setAssemblyId(String assemblyId) { this.assemblyId = assemblyId; }

    public String getBackgroundId() { return backgroundId; }
    public void setBackgroundId(String backgroundId) { this.backgroundId = backgroundId; }

    public String getZoneType() { return zoneType; }
    public void setZoneType(String zoneType) { this.zoneType = zoneType; }

    public Integer getShrinkDistanceCm() { return shrinkDistanceCm; }
    public void setShrinkDistanceCm(Integer shrinkDistanceCm) { this.shrinkDistanceCm = shrinkDistanceCm; }

    public Float getMinX() { return minX; }
    public void setMinX(Float minX) { this.minX = minX; }

    public Float getMaxX() { return maxX; }
    public void setMaxX(Float maxX) { this.maxX = maxX; }

    public Float getMinY() { return minY; }
    public void setMinY(Float minY) { this.minY = minY; }

    public Float getMaxY() { return maxY; }
    public void setMaxY(Float maxY) { this.maxY = maxY; }

    public Float getMinZ() { return minZ; }
    public void setMinZ(Float minZ) { this.minZ = minZ; }

    public Float getMaxZ() { return maxZ; }
    public void setMaxZ(Float maxZ) { this.maxZ = maxZ; }

    public String getCameraDeviceId() { return cameraDeviceId; }
    public void setCameraDeviceId(String cameraDeviceId) { this.cameraDeviceId = cameraDeviceId; }

    public Integer getCameraChannel() { return cameraChannel; }
    public void setCameraChannel(Integer cameraChannel) { this.cameraChannel = cameraChannel; }

    public String getCoordinateTransform() { return coordinateTransform; }
    public void setCoordinateTransform(String coordinateTransform) { this.coordinateTransform = coordinateTransform; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    public static RadarDefenseZone fromResultSet(ResultSet rs) throws SQLException {
        RadarDefenseZone zone = new RadarDefenseZone();
        zone.setId(rs.getInt("id"));
        zone.setZoneId(rs.getString("zone_id"));
        zone.setDeviceId(rs.getString("device_id"));
        zone.setAssemblyId(rs.getString("assembly_id"));
        zone.setBackgroundId(rs.getString("background_id"));
        zone.setZoneType(rs.getString("zone_type"));
        zone.setShrinkDistanceCm(rs.getObject("shrink_distance_cm") != null ? rs.getInt("shrink_distance_cm") : null);
        zone.setMinX(rs.getObject("min_x") != null ? rs.getFloat("min_x") : null);
        zone.setMaxX(rs.getObject("max_x") != null ? rs.getFloat("max_x") : null);
        zone.setMinY(rs.getObject("min_y") != null ? rs.getFloat("min_y") : null);
        zone.setMaxY(rs.getObject("max_y") != null ? rs.getFloat("max_y") : null);
        zone.setMinZ(rs.getObject("min_z") != null ? rs.getFloat("min_z") : null);
        zone.setMaxZ(rs.getObject("max_z") != null ? rs.getFloat("max_z") : null);
        zone.setCameraDeviceId(rs.getString("camera_device_id"));
        zone.setCameraChannel(rs.getObject("camera_channel") != null ? rs.getInt("camera_channel") : null);
        zone.setCoordinateTransform(rs.getString("coordinate_transform"));
        zone.setEnabled(rs.getInt("enabled") == 1);
        zone.setName(rs.getString("name"));
        zone.setDescription(rs.getString("description"));
        zone.setCreatedAt(rs.getTimestamp("created_at"));
        zone.setUpdatedAt(rs.getTimestamp("updated_at"));
        return zone;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("zoneId", zoneId);
        map.put("deviceId", deviceId);
        map.put("assemblyId", assemblyId);
        map.put("backgroundId", backgroundId);
        map.put("zoneType", zoneType);
        map.put("shrinkDistanceCm", shrinkDistanceCm);
        map.put("minX", minX);
        map.put("maxX", maxX);
        map.put("minY", minY);
        map.put("maxY", maxY);
        map.put("minZ", minZ);
        map.put("maxZ", maxZ);
        map.put("cameraDeviceId", cameraDeviceId);
        map.put("cameraChannel", cameraChannel);
        map.put("coordinateTransform", coordinateTransform);
        map.put("enabled", enabled);
        map.put("name", name);
        map.put("description", description);
        if (createdAt != null) map.put("createdAt", createdAt.toString());
        if (updatedAt != null) map.put("updatedAt", updatedAt.toString());
        return map;
    }
}
