package com.digital.video.gateway.driver.livox.model;

import java.sql.Timestamp;

/**
 * 侵入事件数据模型
 */
public class IntrusionEvent {
    private String recordId;
    private String deviceId;
    private String assemblyId;
    private String zoneId;
    private String clusterId;
    private PointCluster cluster; // 聚类信息
    private Timestamp detectedAt;

    public IntrusionEvent() {
        this.detectedAt = new Timestamp(System.currentTimeMillis());
    }

    public IntrusionEvent(String recordId, String deviceId, String zoneId, PointCluster cluster) {
        this.recordId = recordId;
        this.deviceId = deviceId;
        this.zoneId = zoneId;
        this.cluster = cluster;
        this.clusterId = cluster != null ? cluster.getClusterId() : null;
        this.detectedAt = new Timestamp(System.currentTimeMillis());
    }

    public String getRecordId() {
        return recordId;
    }

    public void setRecordId(String recordId) {
        this.recordId = recordId;
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

    public String getZoneId() {
        return zoneId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    public String getClusterId() {
        return clusterId;
    }

    public void setClusterId(String clusterId) {
        this.clusterId = clusterId;
    }

    public PointCluster getCluster() {
        return cluster;
    }

    public void setCluster(PointCluster cluster) {
        this.cluster = cluster;
        this.clusterId = cluster != null ? cluster.getClusterId() : null;
    }

    public Timestamp getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(Timestamp detectedAt) {
        this.detectedAt = detectedAt;
    }
}
