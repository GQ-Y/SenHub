package com.digital.video.gateway.service;

import com.digital.video.gateway.database.AlarmRecord;
import com.digital.video.gateway.database.Database;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 报警记录服务
 */
public class AlarmRecordService {
    private static final Logger logger = LoggerFactory.getLogger(AlarmRecordService.class);
    private final Database database;

    public AlarmRecordService(Database database) {
        this.database = database;
    }

    /**
     * 获取报警记录列表
     */
    public List<AlarmRecord> getAlarmRecords(String deviceId, String assemblyId, String alarmType, String startTime, String endTime, Integer limit) {
        List<AlarmRecord> records = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM alarm_records WHERE 1=1");
        List<String> params = new ArrayList<>();

        if (deviceId != null && !deviceId.isEmpty()) {
            sql.append(" AND device_id = ?");
            params.add(deviceId);
        }

        if (assemblyId != null && !assemblyId.isEmpty()) {
            sql.append(" AND assembly_id = ?");
            params.add(assemblyId);
        }

        if (alarmType != null && !alarmType.isEmpty()) {
            sql.append(" AND alarm_type = ?");
            params.add(alarmType);
        }

        if (startTime != null && !startTime.isEmpty()) {
            sql.append(" AND recorded_at >= ?");
            params.add(startTime);
        }

        if (endTime != null && !endTime.isEmpty()) {
            sql.append(" AND recorded_at <= ?");
            params.add(endTime);
        }

        sql.append(" ORDER BY recorded_at DESC");

        if (limit != null && limit > 0) {
            sql.append(" LIMIT ?");
        }

        Connection conn = database.getConnection(); try (
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            int paramIndex = 1;
            for (String param : params) {
                pstmt.setString(paramIndex++, param);
            }
            if (limit != null && limit > 0) {
                pstmt.setInt(paramIndex, limit);
            }
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                records.add(AlarmRecord.fromResultSet(rs));
            }
        } catch (SQLException e) {
            logger.error("获取报警记录列表失败", e);
        }
        return records;
    }

    /**
     * 获取报警记录详情
     */
    public AlarmRecord getAlarmRecord(String recordId) {
        String sql = "SELECT * FROM alarm_records WHERE alarm_id = ?";
        Connection conn = database.getConnection(); try (
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, recordId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return AlarmRecord.fromResultSet(rs);
            }
        } catch (SQLException e) {
            logger.error("获取报警记录详情失败: {}", recordId, e);
        }
        return null;
    }

    /**
     * 创建报警记录
     */
    public AlarmRecord createAlarmRecord(AlarmRecord record) {
        if (record.getAlarmId() == null || record.getAlarmId().isEmpty()) {
            record.setAlarmId(UUID.randomUUID().toString());
        }
        String sql = "INSERT INTO alarm_records (alarm_id, device_id, assembly_id, alarm_type, alarm_level, channel, alarm_data, capture_url, video_url, status, mqtt_sent, speaker_triggered) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        Connection conn = database.getConnection(); try (
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, record.getAlarmId());
            pstmt.setString(2, record.getDeviceId());
            pstmt.setString(3, record.getAssemblyId());
            pstmt.setString(4, record.getAlarmType());
            pstmt.setString(5, record.getAlarmLevel() != null ? record.getAlarmLevel() : "warning");
            if (record.getChannel() != null) {
                pstmt.setInt(6, record.getChannel());
            } else {
                pstmt.setNull(6, Types.INTEGER);
            }
            pstmt.setString(7, record.getAlarmData());
            pstmt.setString(8, record.getCaptureUrl());
            pstmt.setString(9, record.getVideoUrl());
            pstmt.setString(10, record.getStatus() != null ? record.getStatus() : "pending");
            pstmt.setInt(11, record.isMqttSent() ? 1 : 0);
            pstmt.setInt(12, record.isSpeakerTriggered() ? 1 : 0);
            pstmt.executeUpdate();
            return getAlarmRecord(record.getAlarmId());
        } catch (SQLException e) {
            logger.error("创建报警记录失败", e);
            return null;
        }
    }

    /**
     * 更新报警记录
     */
    public AlarmRecord updateAlarmRecord(String recordId, AlarmRecord record) {
        String sql = "UPDATE alarm_records SET capture_url = ?, video_url = ?, status = ?, mqtt_sent = ?, speaker_triggered = ?, processed_at = CURRENT_TIMESTAMP WHERE alarm_id = ?";
        Connection conn = database.getConnection(); try (
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, record.getCaptureUrl());
            pstmt.setString(2, record.getVideoUrl());
            pstmt.setString(3, record.getStatus());
            pstmt.setInt(4, record.isMqttSent() ? 1 : 0);
            pstmt.setInt(5, record.isSpeakerTriggered() ? 1 : 0);
            pstmt.setString(6, recordId);
            pstmt.executeUpdate();
            return getAlarmRecord(recordId);
        } catch (SQLException e) {
            logger.error("更新报警记录失败: {}", recordId, e);
            return null;
        }
    }
}
