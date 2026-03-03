package com.digital.video.gateway.driver.livox.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 防区配置数据模型
 */
public class DefenseZone {
    public static final String ZONE_TYPE_SHRINK = "shrink"; // 缩小距离方式
    public static final String ZONE_TYPE_BOUNDING_BOX = "bounding_box"; // 边界框方式

    private String zoneId;
    private String deviceId;
    private String assemblyId;
    private String backgroundId;
    private String zoneType; // 'shrink' 或 'bounding_box'
    
    // 缩小距离方式参数
    private Integer shrinkDistanceCm; // 缩小距离（厘米）
    
    // 边界框方式参数
    private Float minX;
    private Float maxX;
    private Float minY;
    private Float maxY;
    private Float minZ;
    private Float maxZ;
    
    // 关联摄像头
    private String cameraDeviceId;
    private Integer cameraChannel;
    
    // 坐标系转换参数（JSON字符串）
    private String coordinateTransform;
    
    private Boolean enabled;
    private String name;
    private String description;

    public DefenseZone() {
        this.enabled = true;
        this.cameraChannel = 1;
    }

    /**
     * 判断点是否在防区内
     */
    public boolean isPointInZone(Point point) {
        if (!enabled) {
            return false;
        }

        if (ZONE_TYPE_SHRINK.equals(zoneType)) {
            // 缩小距离方式：检查点是否在缩小后的防区边界外（即侵入区域）
            float distance = point.distance();
            float zoneDistance = shrinkDistanceCm != null ? shrinkDistanceCm / 100.0f : 0;
            return distance > zoneDistance;
        } else if (ZONE_TYPE_BOUNDING_BOX.equals(zoneType)) {
            // 边界框方式：检查点是否在边界框内
            if (minX != null && point.x < minX) return false;
            if (maxX != null && point.x > maxX) return false;
            if (minY != null && point.y < minY) return false;
            if (maxY != null && point.y > maxY) return false;
            if (minZ != null && point.z < minZ) return false;
            if (maxZ != null && point.z > maxZ) return false;
            return true;
        }

        return false;
    }

    /**
     * 解析坐标系转换参数
     */
    public CoordinateTransform getCoordinateTransform() {
        if (coordinateTransform == null || coordinateTransform.trim().isEmpty()) {
            return new CoordinateTransform(); // 默认无转换
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode node = (ObjectNode) mapper.readTree(coordinateTransform);
            CoordinateTransform transform = new CoordinateTransform();
            if (node.has("translation")) {
                ObjectNode trans = (ObjectNode) node.get("translation");
                transform.translationX = trans.has("x") ? (float) trans.get("x").asDouble() : 0;
                transform.translationY = trans.has("y") ? (float) trans.get("y").asDouble() : 0;
                transform.translationZ = trans.has("z") ? (float) trans.get("z").asDouble() : 0;
            }
            if (node.has("rotation")) {
                ObjectNode rot = (ObjectNode) node.get("rotation");
                transform.rotationX = rot.has("x") ? (float) rot.get("x").asDouble() : 0;
                transform.rotationY = rot.has("y") ? (float) rot.get("y").asDouble() : 0;
                transform.rotationZ = rot.has("z") ? (float) rot.get("z").asDouble() : 0;
            }
            transform.scale = node.has("scale") ? (float) node.get("scale").asDouble() : 1.0f;

            if (node.has("zoomCalibration") && node.get("zoomCalibration").isArray()) {
                var arr = node.get("zoomCalibration");
                for (var item : arr) {
                    if (item.has("distance") && item.has("zoom")) {
                        transform.addZoomCalibPoint(
                                (float) item.get("distance").asDouble(),
                                (float) item.get("zoom").asDouble());
                    }
                }
            }

            return transform;
        } catch (Exception e) {
            return new CoordinateTransform(); // 解析失败，返回默认值
        }
    }

    // Getters and Setters
    public String getZoneId() {
        return zoneId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getAssemblyId() {
        return assemblyId;
    }

    public void setAssemblyId(String assemblyId) {
        this.assemblyId = assemblyId;
    }

    public String getBackgroundId() {
        return backgroundId;
    }

    public void setBackgroundId(String backgroundId) {
        this.backgroundId = backgroundId;
    }

    public String getZoneType() {
        return zoneType;
    }

    public void setZoneType(String zoneType) {
        this.zoneType = zoneType;
    }

    public Integer getShrinkDistanceCm() {
        return shrinkDistanceCm;
    }

    public void setShrinkDistanceCm(Integer shrinkDistanceCm) {
        this.shrinkDistanceCm = shrinkDistanceCm;
    }

    public Float getMinX() {
        return minX;
    }

    public void setMinX(Float minX) {
        this.minX = minX;
    }

    public Float getMaxX() {
        return maxX;
    }

    public void setMaxX(Float maxX) {
        this.maxX = maxX;
    }

    public Float getMinY() {
        return minY;
    }

    public void setMinY(Float minY) {
        this.minY = minY;
    }

    public Float getMaxY() {
        return maxY;
    }

    public void setMaxY(Float maxY) {
        this.maxY = maxY;
    }

    public Float getMinZ() {
        return minZ;
    }

    public void setMinZ(Float minZ) {
        this.minZ = minZ;
    }

    public Float getMaxZ() {
        return maxZ;
    }

    public void setMaxZ(Float maxZ) {
        this.maxZ = maxZ;
    }

    public String getCameraDeviceId() {
        return cameraDeviceId;
    }

    public void setCameraDeviceId(String cameraDeviceId) {
        this.cameraDeviceId = cameraDeviceId;
    }

    public Integer getCameraChannel() {
        return cameraChannel;
    }

    public void setCameraChannel(Integer cameraChannel) {
        this.cameraChannel = cameraChannel;
    }

    public String getCoordinateTransformJson() {
        return coordinateTransform;
    }

    public void setCoordinateTransformJson(String coordinateTransform) {
        this.coordinateTransform = coordinateTransform;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
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

    /**
     * 坐标系转换参数内部类
     */
    public static class CoordinateTransform {
        public float translationX = 0;
        public float translationY = 0;
        public float translationZ = 0;
        public float rotationX = 0;
        public float rotationY = 0;
        public float rotationZ = 0;
        public float scale = 1.0f;
        public java.util.List<float[]> zoomCalibPoints;

        public void addZoomCalibPoint(float distance, float zoom) {
            if (zoomCalibPoints == null) zoomCalibPoints = new java.util.ArrayList<>();
            zoomCalibPoints.add(new float[]{distance, zoom});
        }
    }
}
