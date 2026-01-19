package com.digital.video.gateway.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 流程执行器
 */
public class FlowExecutor {
    private static final Logger logger = LoggerFactory.getLogger(FlowExecutor.class);

    private final Map<String, FlowNodeHandler> handlers = new HashMap<>();
    private int maxDepth = 50; // 防止循环

    public FlowExecutor() {
    }

    public FlowExecutor(Map<String, FlowNodeHandler> initialHandlers) {
        if (initialHandlers != null) {
            handlers.putAll(initialHandlers);
        }
    }

    public void registerHandler(String nodeType, FlowNodeHandler handler) {
        if (nodeType != null && handler != null) {
            handlers.put(nodeType.toLowerCase(), handler);
        }
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public void execute(FlowDefinition flow, FlowContext context) {
        if (flow == null) {
            logger.warn("流程定义为空，无法执行");
            return;
        }
        FlowNodeDefinition start = flow.getStartNode();
        if (start == null) {
            logger.warn("流程没有起始节点，flowId={}", flow.getFlowId());
            return;
        }
        context.setFlowId(flow.getFlowId());
        executeNode(flow, context, start, new HashSet<>(), 0);
    }

    private void executeNode(FlowDefinition flow, FlowContext context, FlowNodeDefinition node,
                             Set<String> visiting, int depth) {
        if (node == null) {
            return;
        }
        if (depth > maxDepth) {
            logger.warn("流程执行深度超过限制，可能存在循环: flowId={}, nodeId={}", flow.getFlowId(), node.getNodeId());
            return;
        }
        if (visiting.contains(node.getNodeId())) {
            logger.warn("检测到流程循环，终止当前分支: nodeId={}", node.getNodeId());
            return;
        }

        visiting.add(node.getNodeId());
        boolean success = false;

        FlowNodeHandler handler = handlers.get(node.getNodeType() != null ? node.getNodeType().toLowerCase() : null);
        if (handler == null) {
            logger.warn("未找到节点处理器，跳过节点: type={}, nodeId={}", node.getNodeType(), node.getNodeId());
        } else {
            try {
                logger.info("执行节点: type={}, nodeId={}", node.getNodeType(), node.getNodeId());
                success = handler.execute(node, context);
            } catch (Exception e) {
                logger.error("执行节点失败: nodeId={}", node.getNodeId(), e);
                success = false;
            }
        }

        List<FlowNodeDefinition> nextNodes = flow.getNextNodes(node.getNodeId(), success);
        if (nextNodes == null || nextNodes.isEmpty()) {
            logger.debug("流程分支结束: nodeId={}", node.getNodeId());
            return;
        }

        for (FlowNodeDefinition next : nextNodes) {
            // 为避免全局visited阻塞其他分支，这里使用新的集合继承当前路径
            Set<String> nextVisiting = new HashSet<>(visiting);
            executeNode(flow, context, next, nextVisiting, depth + 1);
        }
    }
}
