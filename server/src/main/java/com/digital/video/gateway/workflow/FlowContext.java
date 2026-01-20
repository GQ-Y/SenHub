package com.digital.video.gateway.workflow;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
    
    // 异步执行支持字段
    private FlowDefinition flowDefinition;
    private FlowNodeDefinition currentNode;
    private FlowExecutor executor;
    private Set<String> visitedNodes;
    private int currentDepth;

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
    
    // 异步执行支持方法
    
    public FlowDefinition getFlowDefinition() {
        return flowDefinition;
    }
    
    public void setFlowDefinition(FlowDefinition flowDefinition) {
        this.flowDefinition = flowDefinition;
    }
    
    public FlowNodeDefinition getCurrentNode() {
        return currentNode;
    }
    
    public void setCurrentNode(FlowNodeDefinition currentNode) {
        this.currentNode = currentNode;
    }
    
    public FlowExecutor getExecutor() {
        return executor;
    }
    
    public void setExecutor(FlowExecutor executor) {
        this.executor = executor;
    }
    
    public Set<String> getVisitedNodes() {
        return visitedNodes;
    }
    
    public void setVisitedNodes(Set<String> visitedNodes) {
        this.visitedNodes = visitedNodes;
    }
    
    public int getCurrentDepth() {
        return currentDepth;
    }
    
    public void setCurrentDepth(int currentDepth) {
        this.currentDepth = currentDepth;
    }
}
