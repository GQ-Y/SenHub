package com.digital.video.gateway.database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

/**
 * 录像任务实体类
 */
public class RecordingTask {
    private int id;
    private String taskId;
    private String deviceId;
    private int channel;
    private String startTime;
    private String endTime;
    private String localFilePath;
    private String ossUrl;
    private int status; // 0: pending, 1: downloading, 2: completed, 3: failed
    private int progress;
    private Integer downloadHandle;
    private String errorMessage;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public int getChannel() {
        return channel;
    }

    public void setChannel(int channel) {
        this.channel = channel;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public String getLocalFilePath() {
        return localFilePath;
    }

    public void setLocalFilePath(String localFilePath) {
        this.localFilePath = localFilePath;
    }

    public String getOssUrl() {
        return ossUrl;
    }

    public void setOssUrl(String ossUrl) {
        this.ossUrl = ossUrl;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public Integer getDownloadHandle() {
        return downloadHandle;
    }

    public void setDownloadHandle(Integer downloadHandle) {
        this.downloadHandle = downloadHandle;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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
    public static RecordingTask fromResultSet(ResultSet rs) throws SQLException {
        RecordingTask task = new RecordingTask();
        task.setId(rs.getInt("id"));
        task.setTaskId(rs.getString("task_id"));
        task.setDeviceId(rs.getString("device_id"));
        task.setChannel(rs.getInt("channel"));
        task.setStartTime(rs.getString("start_time"));
        task.setEndTime(rs.getString("end_time"));
        task.setLocalFilePath(rs.getString("local_file_path"));
        task.setOssUrl(rs.getString("oss_url"));
        task.setStatus(rs.getInt("status"));
        task.setProgress(rs.getInt("progress"));
        Integer handle = rs.getInt("download_handle");
        task.setDownloadHandle(rs.wasNull() ? null : handle);
        task.setErrorMessage(rs.getString("error_message"));
        task.setCreatedAt(rs.getTimestamp("created_at"));
        task.setUpdatedAt(rs.getTimestamp("updated_at"));
        return task;
    }

    /**
     * 转换为Map（用于JSON响应）
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", String.valueOf(id));
        map.put("taskId", taskId);
        map.put("deviceId", deviceId);
        map.put("channel", channel);
        map.put("startTime", startTime);
        map.put("endTime", endTime);
        map.put("localFilePath", localFilePath);
        map.put("ossUrl", ossUrl);
        map.put("status", status);
        map.put("progress", progress);
        map.put("downloadHandle", downloadHandle);
        map.put("errorMessage", errorMessage);
        if (createdAt != null)
            map.put("createdAt", createdAt.toString());
        if (updatedAt != null)
            map.put("updatedAt", updatedAt.toString());
        return map;
    }
}
