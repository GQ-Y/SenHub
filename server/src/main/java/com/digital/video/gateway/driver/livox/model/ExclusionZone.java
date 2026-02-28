package com.digital.video.gateway.driver.livox.model;

/**
 * 空间排除区（白名单条目）。
 * 一个 3D 轴对齐包围盒（AABB），落入该包围盒内的聚类将被过滤，不触发报警。
 * 由操作员在前端将持久目标"加白"时创建。
 */
public class ExclusionZone {

    private String exclusionId;
    private String zoneId;
    private String sourceTrackingId;
    private String label;

    private float minX, maxX;
    private float minY, maxY;
    private float minZ, maxZ;

    private long createdAt;

    public ExclusionZone() {
        this.createdAt = System.currentTimeMillis();
    }

    /**
     * 从跟踪目标的聚类包围盒创建排除区，自动加 margin。
     */
    public static ExclusionZone fromCluster(String zoneId, String trackingId,
                                            PointCluster cluster, float margin) {
        ExclusionZone ez = new ExclusionZone();
        ez.exclusionId = "exc_" + System.currentTimeMillis() + "_" + trackingId;
        ez.zoneId = zoneId;
        ez.sourceTrackingId = trackingId;

        PointCluster.BoundingBox bbox = cluster.getBbox();
        if (bbox != null) {
            ez.minX = bbox.minX - margin;
            ez.maxX = bbox.maxX + margin;
            ez.minY = bbox.minY - margin;
            ez.maxY = bbox.maxY + margin;
            ez.minZ = bbox.minZ - margin;
            ez.maxZ = bbox.maxZ + margin;
        } else {
            Point c = cluster.getCentroid();
            float r = Math.max(0.5f, margin);
            ez.minX = c.x - r;
            ez.maxX = c.x + r;
            ez.minY = c.y - r;
            ez.maxY = c.y + r;
            ez.minZ = c.z - r;
            ez.maxZ = c.z + r;
        }

        TargetType type = cluster.getTargetType();
        ez.label = (type != null ? type.getLabel() : "目标") + " @ " + cluster.getCentroid();
        return ez;
    }

    /**
     * 手动指定包围盒创建排除区。
     */
    public static ExclusionZone manual(String zoneId, String label,
                                       float minX, float maxX,
                                       float minY, float maxY,
                                       float minZ, float maxZ) {
        ExclusionZone ez = new ExclusionZone();
        ez.exclusionId = "exc_" + System.currentTimeMillis() + "_manual";
        ez.zoneId = zoneId;
        ez.label = label;
        ez.minX = minX;
        ez.maxX = maxX;
        ez.minY = minY;
        ez.maxY = maxY;
        ez.minZ = minZ;
        ez.maxZ = maxZ;
        return ez;
    }

    /**
     * 判断一个点是否落入排除区。
     */
    public boolean contains(Point p) {
        return p.x >= minX && p.x <= maxX
                && p.y >= minY && p.y <= maxY
                && p.z >= minZ && p.z <= maxZ;
    }

    /**
     * 判断一个聚类的质心是否落入排除区。
     */
    public boolean containsCluster(PointCluster cluster) {
        if (cluster == null || cluster.getCentroid() == null) return false;
        return contains(cluster.getCentroid());
    }

    // ---- Getters / Setters ----

    public String getExclusionId() { return exclusionId; }
    public void setExclusionId(String exclusionId) { this.exclusionId = exclusionId; }

    public String getZoneId() { return zoneId; }
    public void setZoneId(String zoneId) { this.zoneId = zoneId; }

    public String getSourceTrackingId() { return sourceTrackingId; }
    public void setSourceTrackingId(String sourceTrackingId) { this.sourceTrackingId = sourceTrackingId; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public float getMinX() { return minX; }
    public void setMinX(float minX) { this.minX = minX; }
    public float getMaxX() { return maxX; }
    public void setMaxX(float maxX) { this.maxX = maxX; }
    public float getMinY() { return minY; }
    public void setMinY(float minY) { this.minY = minY; }
    public float getMaxY() { return maxY; }
    public void setMaxY(float maxY) { this.maxY = maxY; }
    public float getMinZ() { return minZ; }
    public void setMinZ(float minZ) { this.minZ = minZ; }
    public float getMaxZ() { return maxZ; }
    public void setMaxZ(float maxZ) { this.maxZ = maxZ; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
