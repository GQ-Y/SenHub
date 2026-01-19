package com.digital.video.gateway.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 流程定义
 */
public class FlowDefinition {
    private static final Logger logger = LoggerFactory.getLogger(FlowDefinition.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private String flowId;
    private String name;
    private List<FlowNodeDefinition> nodes = new ArrayList<>();
    private List<FlowConnection> connections = new ArrayList<>();

    public String getFlowId() {
        return flowId;
    }

    public void setFlowId(String flowId) {
        this.flowId = flowId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<FlowNodeDefinition> getNodes() {
        return nodes;
    }

    public void setNodes(List<FlowNodeDefinition> nodes) {
        this.nodes = nodes != null ? nodes : new ArrayList<>();
    }

    public List<FlowConnection> getConnections() {
        return connections;
    }

    public void setConnections(List<FlowConnection> connections) {
        this.connections = connections != null ? connections : new ArrayList<>();
    }

    /**
     * 获取起始节点
     */
    public FlowNodeDefinition getStartNode() {
        Optional<FlowNodeDefinition> event = nodes.stream()
                .filter(n -> "event_trigger".equalsIgnoreCase(n.getNodeType()))
                .findFirst();
        if (event.isPresent()) {
            return event.get();
        }
        return nodes.isEmpty() ? null : nodes.get(0);
    }

    /**
     * 根据nodeId获取节点
     */
    public FlowNodeDefinition getNodeById(String nodeId) {
        if (nodeId == null) {
            return null;
        }
        return nodes.stream()
                .filter(n -> nodeId.equals(n.getNodeId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取下一个可执行节点（根据执行结果过滤condition）
     */
    public List<FlowNodeDefinition> getNextNodes(String nodeId, boolean success) {
        if (connections == null || connections.isEmpty()) {
            return new ArrayList<>();
        }
        String conditionValue = success ? "success" : "failure";
        List<String> nextIds = connections.stream()
                .filter(c -> nodeId.equals(c.getFrom()))
                .filter(c -> c.getCondition() == null
                        || c.getCondition().isEmpty()
                        || conditionValue.equalsIgnoreCase(c.getCondition()))
                .map(FlowConnection::getTo)
                .collect(Collectors.toList());

        return nodes.stream()
                .filter(n -> nextIds.contains(n.getNodeId()))
                .collect(Collectors.toList());
    }

    /**
     * 根据JSON字符串构建FlowDefinition
     */
    public static FlowDefinition fromJsonStrings(String flowId, String name, String nodesJson, String connectionsJson) {
        FlowDefinition definition = new FlowDefinition();
        definition.setFlowId(flowId);
        definition.setName(name);
        try {
            if (nodesJson != null && !nodesJson.isEmpty()) {
                List<Map<String, Object>> rawNodes = mapper.readValue(nodesJson, new TypeReference<>() {});
                List<FlowNodeDefinition> nodeDefinitions = new ArrayList<>();
                for (Map<String, Object> raw : rawNodes) {
                    String nodeId = raw.get("nodeId") != null ? raw.get("nodeId").toString() : null;
                    String type = raw.get("type") != null ? raw.get("type").toString() : null;
                    Map<String, Object> config = raw.get("config") instanceof Map
                            ? (Map<String, Object>) raw.get("config")
                            : null;
                    nodeDefinitions.add(new FlowNodeDefinition(nodeId, type, config));
                }
                definition.setNodes(nodeDefinitions);
            }

            if (connectionsJson != null && !connectionsJson.isEmpty()) {
                List<Map<String, Object>> rawConnections = mapper.readValue(connectionsJson, new TypeReference<>() {});
                List<FlowConnection> connDefinitions = new ArrayList<>();
                for (Map<String, Object> raw : rawConnections) {
                    String from = raw.get("from") != null ? raw.get("from").toString() : null;
                    String to = raw.get("to") != null ? raw.get("to").toString() : null;
                    String condition = raw.get("condition") != null ? raw.get("condition").toString() : null;
                    connDefinitions.add(new FlowConnection(from, to, condition));
                }
                definition.setConnections(connDefinitions);
            }
        } catch (Exception e) {
            logger.error("解析流程定义JSON失败: flowId={}", flowId, e);
        }
        return definition;
    }
}
