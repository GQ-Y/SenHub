package com.digital.video.gateway.database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

/**
 * 雷达侵入记录实体类
 */
public class RadarIntrusionRecord {
    private int id;
    private String recordId;
    private String deviceId;
    private String assemblyId;
    private String zoneId;
    private String clusterId;
    private float centroidX;
    private float centroidY;
    private float centroidZ;
    private Float volume;
    private Float bboxMinX, bboxMinY, bboxMinZ;
    private Float bboxMaxX, bboxMaxY, bboxMaxZ;
    private Integer pointCount;
    private Timestamp detectedAt;

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getRecordId() { return recordId; }
    public void setRecordId(String recordId) { this.recordId = recordId; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getAssemblyId() { return assemblyId; }
    public void setAssemblyId(String assemblyId) { this.assemblyId = assemblyId; }

    public String getZoneId() { return zoneId; }
    public void setZoneId(String zoneId) { this.zoneId = zoneId; }

    public String getClusterId() { return clusterId; }
    public void setClusterId(String clusterId) { this.clusterId = clusterId; }

    public float getCentroidX() { return centroidX; }
    public void setCentroidX(float centroidX) { this.centroidX = centroidX; }

    public float getCentroidY() { return centroidY; }
    public void setCentroidY(float centroidY) { this.centroidY = centroidY; }

    public float getCentroidZ() { return centroidZ; }
    public void setCentroidZ(float centroidZ) { this.centroidZ = centroidZ; }

    public Float getVolume() { return volume; }
    public void setVolume(Float volume) { this.volume = volume; }

    public Float getBboxMinX() { return bboxMinX; }
    public void setBboxMinX(Float bboxMinX) { this.bboxMinX = bboxMinX; }

    public Float getBboxMinY() { return bboxMinY; }
    public void setBboxMinY(Float bboxMinY) { this.bboxMinY = bboxMinY; }

    public Float getBboxMinZ() { return bboxMinZ; }
    public void setBboxMinZ(Float bboxMinZ) { this.bboxMinZ = bboxMinZ; }

    public Float getBboxMaxX() { return bboxMaxX; }
    public void setBboxMaxX(Float bboxMaxX) { this.bboxMaxX = bboxMaxX; }

    public Float getBboxMaxY() { return bboxMaxY; }
    public void setBboxMaxY(Float bboxMaxY) { this.bboxMaxY = bboxMaxY; }

    public Float getBboxMaxZ() { return bboxMaxZ; }
    public void setBboxMaxZ(Float bboxMaxZ) { this.bboxMaxZ = bboxMaxZ; }

    public Integer getPointCount() { return pointCount; }
    public void setPointCount(Integer pointCount) { this.pointCount = pointCount; }

    public Timestamp getDetectedAt() { return detectedAt; }
    public void setDetectedAt(Timestamp detectedAt) { this.detectedAt = detectedAt; }

    public static RadarIntrusionRecord fromResultSet(ResultSet rs) throws SQLException {
        RadarIntrusionRecord record = new RadarIntrusionRecord();
        record.setId(rs.getInt("id"));
        record.setRecordId(rs.getString("record_id"));
        record.setDeviceId(rs.getString("device_id"));
        record.setAssemblyId(rs.getString("assembly_id"));
        record.setZoneId(rs.getString("zone_id"));
        record.setClusterId(rs.getString("cluster_id"));
        record.setCentroidX(rs.getFloat("centroid_x"));
        record.setCentroidY(rs.getFloat("centroid_y"));
        record.setCentroidZ(rs.getFloat("centroid_z"));
        record.setVolume(rs.getObject("volume") != null ? rs.getFloat("volume") : null);
        record.setBboxMinX(rs.getObject("bbox_min_x") != null ? rs.getFloat("bbox_min_x") : null);
        record.setBboxMinY(rs.getObject("bbox_min_y") != null ? rs.getFloat("bbox_min_y") : null);
        record.setBboxMinZ(rs.getObject("bbox_min_z") != null ? rs.getFloat("bbox_min_z") : null);
        record.setBboxMaxX(rs.getObject("bbox_max_x") != null ? rs.getFloat("bbox_max_x") : null);
        record.setBboxMaxY(rs.getObject("bbox_max_y") != null ? rs.getFloat("bbox_max_y") : null);
        record.setBboxMaxZ(rs.getObject("bbox_max_z") != null ? rs.getFloat("bbox_max_z") : null);
        record.setPointCount(rs.getObject("point_count") != null ? rs.getInt("point_count") : null);
        record.setDetectedAt(rs.getTimestamp("detected_at"));
        return record;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("recordId", recordId);
        map.put("deviceId", deviceId);
        map.put("assemblyId", assemblyId);
        map.put("zoneId", zoneId);
        map.put("clusterId", clusterId);
        map.put("centroid", Map.of("x", centroidX, "y", centroidY, "z", centroidZ));
        map.put("volume", volume);
        map.put("pointCount", pointCount);
        if (bboxMinX != null) {
            map.put("bbox", Map.of(
                "min", Map.of("x", bboxMinX, "y", bboxMinY, "z", bboxMinZ),
                "max", Map.of("x", bboxMaxX, "y", bboxMaxY, "z", bboxMaxZ)
            ));
        }
        if (detectedAt != null) map.put("detectedAt", detectedAt.toString());
        return map;
    }
}
