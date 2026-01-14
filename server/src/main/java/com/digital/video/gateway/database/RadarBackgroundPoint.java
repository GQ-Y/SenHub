package com.digital.video.gateway.database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * 雷达背景点云数据实体类
 */
public class RadarBackgroundPoint {
    private int id;
    private String backgroundId;
    private String gridKey; // 网格索引键
    private float centerX;
    private float centerY;
    private float centerZ;
    private int pointCount;
    private Float meanDistance;
    private Float stdDeviation;
    private Timestamp createdAt;

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getBackgroundId() { return backgroundId; }
    public void setBackgroundId(String backgroundId) { this.backgroundId = backgroundId; }

    public String getGridKey() { return gridKey; }
    public void setGridKey(String gridKey) { this.gridKey = gridKey; }

    public float getCenterX() { return centerX; }
    public void setCenterX(float centerX) { this.centerX = centerX; }

    public float getCenterY() { return centerY; }
    public void setCenterY(float centerY) { this.centerY = centerY; }

    public float getCenterZ() { return centerZ; }
    public void setCenterZ(float centerZ) { this.centerZ = centerZ; }

    public int getPointCount() { return pointCount; }
    public void setPointCount(int pointCount) { this.pointCount = pointCount; }

    public Float getMeanDistance() { return meanDistance; }
    public void setMeanDistance(Float meanDistance) { this.meanDistance = meanDistance; }

    public Float getStdDeviation() { return stdDeviation; }
    public void setStdDeviation(Float stdDeviation) { this.stdDeviation = stdDeviation; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public static RadarBackgroundPoint fromResultSet(ResultSet rs) throws SQLException {
        RadarBackgroundPoint point = new RadarBackgroundPoint();
        point.setId(rs.getInt("id"));
        point.setBackgroundId(rs.getString("background_id"));
        point.setGridKey(rs.getString("grid_key"));
        point.setCenterX(rs.getFloat("center_x"));
        point.setCenterY(rs.getFloat("center_y"));
        point.setCenterZ(rs.getFloat("center_z"));
        point.setPointCount(rs.getInt("point_count"));
        point.setMeanDistance(rs.getObject("mean_distance") != null ? rs.getFloat("mean_distance") : null);
        point.setStdDeviation(rs.getObject("std_deviation") != null ? rs.getFloat("std_deviation") : null);
        point.setCreatedAt(rs.getTimestamp("created_at"));
        return point;
    }
}
