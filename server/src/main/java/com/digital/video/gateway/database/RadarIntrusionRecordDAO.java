package com.digital.video.gateway.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 雷达侵入记录DAO操作类
 */
public class RadarIntrusionRecordDAO {
    private static final Logger logger = LoggerFactory.getLogger(RadarIntrusionRecordDAO.class);
    private final Connection connection;

    public RadarIntrusionRecordDAO(Connection connection) {
        this.connection = connection;
    }

    /**
     * 保存侵入记录
     */
    public boolean save(RadarIntrusionRecord record) {
        String sql = "INSERT INTO radar_intrusion_records " +
                "(record_id, device_id, assembly_id, zone_id, cluster_id, " +
                "centroid_x, centroid_y, centroid_z, volume, " +
                "bbox_min_x, bbox_min_y, bbox_min_z, bbox_max_x, bbox_max_y, bbox_max_z, " +
                "point_count, duration, detected_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, record.getRecordId());
            pstmt.setString(2, record.getDeviceId());
            pstmt.setString(3, record.getAssemblyId());
            pstmt.setString(4, record.getZoneId());
            pstmt.setString(5, record.getClusterId());
            pstmt.setFloat(6, record.getCentroidX());
            pstmt.setFloat(7, record.getCentroidY());
            pstmt.setFloat(8, record.getCentroidZ());
            pstmt.setObject(9, record.getVolume());
            pstmt.setObject(10, record.getBboxMinX());
            pstmt.setObject(11, record.getBboxMinY());
            pstmt.setObject(12, record.getBboxMinZ());
            pstmt.setObject(13, record.getBboxMaxX());
            pstmt.setObject(14, record.getBboxMaxY());
            pstmt.setObject(15, record.getBboxMaxZ());
            pstmt.setObject(16, record.getPointCount());
            Long duration = record.getDuration();
            pstmt.setObject(17, duration);
            pstmt.setTimestamp(18, record.getDetectedAt());
            pstmt.executeUpdate();
            if (duration != null) {
                logger.debug("保存侵入记录: recordId={}, duration={}ms", record.getRecordId(), duration);
            } else {
                logger.warn("保存侵入记录: recordId={}, duration为null", record.getRecordId());
            }
            return true;
        } catch (SQLException e) {
            logger.error("保存侵入记录失败: {}", record.getRecordId(), e);
            return false;
        }
    }

    /**
     * 根据ID获取记录
     */
    public RadarIntrusionRecord getById(String recordId) {
        String sql = "SELECT * FROM radar_intrusion_records WHERE record_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, recordId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return RadarIntrusionRecord.fromResultSet(rs);
            }
        } catch (SQLException e) {
            logger.error("获取侵入记录失败: {}", recordId, e);
        }
        return null;
    }

    /**
     * 获取侵入记录列表（支持分页和过滤）
     */
    public List<RadarIntrusionRecord> getRecords(String deviceId, String zoneId,
            Date startTime, Date endTime,
            int page, int pageSize) {
        List<RadarIntrusionRecord> records = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM radar_intrusion_records WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (deviceId != null) {
            sql.append(" AND device_id = ?");
            params.add(deviceId);
        }
        if (zoneId != null) {
            sql.append(" AND zone_id = ?");
            params.add(zoneId);
        }
        if (startTime != null) {
            sql.append(" AND detected_at >= ?");
            params.add(new Timestamp(startTime.getTime()));
        }
        if (endTime != null) {
            sql.append(" AND detected_at <= ?");
            params.add(new Timestamp(endTime.getTime()));
        }

        sql.append(" ORDER BY detected_at DESC LIMIT ? OFFSET ?");
        params.add(pageSize);
        params.add((page - 1) * pageSize);

        try (PreparedStatement pstmt = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                records.add(RadarIntrusionRecord.fromResultSet(rs));
            }
        } catch (SQLException e) {
            logger.error("查询侵入记录失败", e);
        }
        return records;
    }

    /**
     * 删除设备的所有侵入记录
     */
    public int deleteAll(String deviceId) {
        String sql = deviceId != null
                ? "DELETE FROM radar_intrusion_records WHERE device_id = ?"
                : "DELETE FROM radar_intrusion_records";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            if (deviceId != null) {
                pstmt.setString(1, deviceId);
            }
            int count = pstmt.executeUpdate();
            logger.info("删除侵入记录: deviceId={}, 删除数量={}", deviceId, count);
            return count;
        } catch (SQLException e) {
            logger.error("删除侵入记录失败: deviceId={}", deviceId, e);
            return 0;
        }
    }
}
