package com.digital.video.gateway.database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

/**
 * 报警记录实体类
 */
public class AlarmRecord {
    private int id;
    private String alarmId;
    private String deviceId;
    private String assemblyId;
    private String alarmType;
    private String alarmLevel; // warning, critical, info
    private Integer channel;
    private String alarmData; // JSON string
    private String captureUrl;
    private String videoUrl;
    private String status; // pending, processed, ignored
    private boolean mqttSent;
    private boolean speakerTriggered;
    private Timestamp recordedAt;
    private Timestamp processedAt;

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getAlarmId() { return alarmId; }
    public void setAlarmId(String alarmId) { this.alarmId = alarmId; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getAssemblyId() { return assemblyId; }
    public void setAssemblyId(String assemblyId) { this.assemblyId = assemblyId; }

    public String getAlarmType() { return alarmType; }
    public void setAlarmType(String alarmType) { this.alarmType = alarmType; }

    public String getAlarmLevel() { return alarmLevel; }
    public void setAlarmLevel(String alarmLevel) { this.alarmLevel = alarmLevel; }

    public Integer getChannel() { return channel; }
    public void setChannel(Integer channel) { this.channel = channel; }

    public String getAlarmData() { return alarmData; }
    public void setAlarmData(String alarmData) { this.alarmData = alarmData; }

    public String getCaptureUrl() { return captureUrl; }
    public void setCaptureUrl(String captureUrl) { this.captureUrl = captureUrl; }

    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isMqttSent() { return mqttSent; }
    public void setMqttSent(boolean mqttSent) { this.mqttSent = mqttSent; }

    public boolean isSpeakerTriggered() { return speakerTriggered; }
    public void setSpeakerTriggered(boolean speakerTriggered) { this.speakerTriggered = speakerTriggered; }

    public Timestamp getRecordedAt() { return recordedAt; }
    public void setRecordedAt(Timestamp recordedAt) { this.recordedAt = recordedAt; }

    public Timestamp getProcessedAt() { return processedAt; }
    public void setProcessedAt(Timestamp processedAt) { this.processedAt = processedAt; }

    /**
     * 从ResultSet构建对象
     */
    public static AlarmRecord fromResultSet(ResultSet rs) throws SQLException {
        AlarmRecord record = new AlarmRecord();
        record.setId(rs.getInt("id"));
        record.setAlarmId(rs.getString("alarm_id"));
        record.setDeviceId(rs.getString("device_id"));
        record.setAssemblyId(rs.getString("assembly_id"));
        record.setAlarmType(rs.getString("alarm_type"));
        record.setAlarmLevel(rs.getString("alarm_level"));
        Integer ch = rs.getInt("channel");
        record.setChannel(rs.wasNull() ? null : ch);
        record.setAlarmData(rs.getString("alarm_data"));
        record.setCaptureUrl(rs.getString("capture_url"));
        record.setVideoUrl(rs.getString("video_url"));
        record.setStatus(rs.getString("status"));
        record.setMqttSent(rs.getInt("mqtt_sent") == 1);
        record.setSpeakerTriggered(rs.getInt("speaker_triggered") == 1);
        record.setRecordedAt(rs.getTimestamp("recorded_at"));
        record.setProcessedAt(rs.getTimestamp("processed_at"));
        return record;
    }

    /**
     * 转换为Map（用于JSON响应）
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", String.valueOf(id));
        map.put("alarmId", alarmId);
        map.put("deviceId", deviceId);
        map.put("assemblyId", assemblyId);
        map.put("alarmType", alarmType);
        map.put("alarmLevel", alarmLevel);
        map.put("channel", channel);
        map.put("alarmData", alarmData);
        map.put("captureUrl", captureUrl);
        map.put("videoUrl", videoUrl);
        map.put("status", status);
        map.put("mqttSent", mqttSent);
        map.put("speakerTriggered", speakerTriggered);
        if (recordedAt != null) map.put("recordedAt", recordedAt.toString());
        if (processedAt != null) map.put("processedAt", processedAt.toString());
        return map;
    }
}
