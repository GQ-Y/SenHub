package com.digital.video.gateway.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 流程执行器
 */
public class FlowExecutor {
    private static final Logger logger = LoggerFactory.getLogger(FlowExecutor.class);

    private final Map<String, FlowNodeHandler> handlers = new HashMap<>();
    private int maxDepth = 50; // 防止循环
    
    // 用于并行执行多个分支
    private static final ExecutorService branchExecutor = Executors.newCachedThreadPool();

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
        
        // 预分析工作流：检查是否包含特定节点类型，供其他节点使用
        analyzeFlowNodes(flow, context);
        
        executeNode(flow, context, start, new HashSet<>(), 0);
    }
    
    /**
     * 分析工作流节点，将节点类型信息保存到context
     * 用于节点间的协调（如：录像节点需要知道是否有webhook节点）
     */
    private void analyzeFlowNodes(FlowDefinition flow, FlowContext context) {
        List<FlowNodeDefinition> nodes = flow.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        
        boolean hasWebhook = false;
        boolean hasOssUpload = false;
        boolean hasMqttPublish = false;
        boolean hasRecord = false;
        boolean hasCapture = false;
        
        for (FlowNodeDefinition node : nodes) {
            String type = node.getNodeType();
            if (type == null) continue;
            
            switch (type.toLowerCase()) {
                case "webhook":
                    hasWebhook = true;
                    break;
                case "oss_upload":
                    hasOssUpload = true;
                    break;
                case "mqtt_publish":
                    hasMqttPublish = true;
                    break;
                case "record":
                    hasRecord = true;
                    break;
                case "capture":
                    hasCapture = true;
                    break;
            }
        }
        
        // 保存到context，供其他节点查询
        context.putVariable("_flow_hasWebhook", hasWebhook);
        context.putVariable("_flow_hasOssUpload", hasOssUpload);
        context.putVariable("_flow_hasMqttPublish", hasMqttPublish);
        context.putVariable("_flow_hasRecord", hasRecord);
        context.putVariable("_flow_hasCapture", hasCapture);
        
        logger.debug("工作流分析完成: flowId={}, hasWebhook={}, hasOssUpload={}, hasRecord={}, hasCapture={}",
                flow.getFlowId(), hasWebhook, hasOssUpload, hasRecord, hasCapture);
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

        if (nextNodes.size() == 1) {
            // 只有一个后续节点，直接顺序执行
            Set<String> nextVisiting = new HashSet<>(visiting);
            executeNode(flow, context, nextNodes.get(0), nextVisiting, depth + 1);
        } else {
            // 多个后续节点，并行执行各分支
            logger.info("检测到多个分支({}个)，并行执行", nextNodes.size());
            List<Future<?>> futures = new ArrayList<>();
            
            for (FlowNodeDefinition next : nextNodes) {
                Set<String> nextVisiting = new HashSet<>(visiting);
                // 每个分支创建独立的context副本，避免并发修改
                FlowContext branchContext = copyContext(context);
                
                Future<?> future = branchExecutor.submit(() -> {
                    try {
                        executeNode(flow, branchContext, next, nextVisiting, depth + 1);
                    } catch (Exception e) {
                        logger.error("分支执行异常: nodeId={}", next.getNodeId(), e);
                    }
                });
                futures.add(future);
            }
            
            // 等待所有分支完成（可选，取决于是否需要同步）
            for (Future<?> future : futures) {
                try {
                    future.get();  // 等待分支完成
                } catch (Exception e) {
                    logger.error("等待分支完成异常", e);
                }
            }
        }
    }
    
    /**
     * 复制FlowContext，用于并行分支
     */
    private FlowContext copyContext(FlowContext original) {
        FlowContext copy = new FlowContext();
        copy.setFlowId(original.getFlowId());
        copy.setDeviceId(original.getDeviceId());
        copy.setAssemblyId(original.getAssemblyId());
        copy.setAlarmType(original.getAlarmType());
        
        // 复制payload
        if (original.getPayload() != null) {
            copy.setPayload(new HashMap<>(original.getPayload()));
        }
        
        // 复制variables（使用ConcurrentHashMap保证线程安全）
        if (original.getVariables() != null) {
            copy.getVariables().putAll(original.getVariables());
        }
        
        return copy;
    }
}
