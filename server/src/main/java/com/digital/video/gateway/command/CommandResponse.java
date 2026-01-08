package com.digital.video.gateway.command;

import java.util.Map;

/**
 * 命令响应类
 */
public class CommandResponse {
    private String requestId;
    private String deviceId;
    private String command;
    private boolean success;
    private Map<String, Object> data;
    private String error;

    // Getters and Setters
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
