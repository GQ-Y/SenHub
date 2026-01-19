package com.digital.video.gateway.workflow;

import java.util.Map;

/**
 * 流程节点定义
 */
public class FlowNodeDefinition {
    private String nodeId;
    private String nodeType;
    private Map<String, Object> config;

    public FlowNodeDefinition() {
    }

    public FlowNodeDefinition(String nodeId, String nodeType, Map<String, Object> config) {
        this.nodeId = nodeId;
        this.nodeType = nodeType;
        this.config = config;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public void setConfig(Map<String, Object> config) {
        this.config = config;
    }
}
