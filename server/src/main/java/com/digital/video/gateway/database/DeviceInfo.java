package com.digital.video.gateway.database;

import java.sql.Timestamp;

/**
 * 设备信息实体类
 */
public class DeviceInfo {
    // 品牌常量
    public static final String BRAND_HIKVISION = "hikvision";
    public static final String BRAND_TIANDY = "tiandy";
    public static final String BRAND_DAHUA = "dahua";
    public static final String BRAND_AUTO = "auto";
    public static final String BRAND_UNKNOWN = "unknown";
    
    private int id;
    private String deviceId;  // 设备ID，通常使用IP地址
    private String ip;
    private int port;
    private String name;
    private String username;
    private String password;
    private String rtspUrl;
    private String status;  // online, offline
    private int userId;  // SDK登录返回的用户ID
    private int channel;  // 通道号（起始通道号）
    private String brand;  // 设备品牌：hikvision, tiandy, dahua, auto, unknown
    private Timestamp lastSeen;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRtspUrl() { return rtspUrl; }
    public void setRtspUrl(String rtspUrl) { this.rtspUrl = rtspUrl; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public int getChannel() { return channel; }
    public void setChannel(int channel) { this.channel = channel; }

    public String getBrand() { return brand != null ? brand : BRAND_AUTO; }
    public void setBrand(String brand) { this.brand = brand; }

    public Timestamp getLastSeen() { return lastSeen; }
    public void setLastSeen(Timestamp lastSeen) { this.lastSeen = lastSeen; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return String.format("DeviceInfo{deviceId='%s', ip='%s', port=%d, name='%s', status='%s', brand='%s'}", 
            deviceId, ip, port, name, status, brand);
    }
}
