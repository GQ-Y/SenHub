package com.digital.video.gateway.workflow;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 流程执行上下文
 */
public class FlowContext {
    private String flowId;
    private String deviceId;
    private String assemblyId;
    private String alarmType;
    private Map<String, Object> payload = new HashMap<>();
    private Map<String, Object> variables = new ConcurrentHashMap<>();

    public String getFlowId() {
        return flowId;
    }

    public void setFlowId(String flowId) {
        this.flowId = flowId;
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

    public String getAlarmType() {
        return alarmType;
    }

    public void setAlarmType(String alarmType) {
        this.alarmType = alarmType;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, Object> variables) {
        this.variables = variables;
    }

    public Object getVariable(String key) {
        return variables.get(key);
    }

    public void putVariable(String key, Object value) {
        variables.put(key, value);
    }
}
