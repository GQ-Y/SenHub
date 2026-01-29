package com.digital.video.gateway.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
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
        
        try {
            executeNode(flow, context, start, new HashSet<>(), 0);
        } finally {
            // 工作流执行完成后，清理临时文件（图片、视频）
            cleanupTempFiles(context);
        }
    }
    
    /**
     * 清理工作流执行过程中产生的临时文件（图片、视频）
     * 这些文件在上传到OSS后就不再需要保存在本地
     */
    @SuppressWarnings("unchecked")
    private void cleanupTempFiles(FlowContext context) {
        List<String> filesToDelete = new ArrayList<>();
        
        // 收集需要删除的文件路径
        Map<String, Object> variables = context.getVariables();
        if (variables != null) {
            // 从并行分支收集的临时文件列表
            Object tempFilesObj = variables.get("_tempFilesToClean");
            if (tempFilesObj instanceof List) {
                filesToDelete.addAll((List<String>) tempFilesObj);
            }
            
            // 主流程中的抓图文件（CaptureHandler 使用 capturePath）
            Object capturePath = variables.get("capturePath");
            if (capturePath instanceof String && !filesToDelete.contains(capturePath)) {
                filesToDelete.add((String) capturePath);
            }
            
            // 主流程中的录像文件
            Object recordFilePath = variables.get("recordFilePath");
            if (recordFilePath instanceof String && !filesToDelete.contains(recordFilePath)) {
                filesToDelete.add((String) recordFilePath);
            }
        }
        
        if (filesToDelete.isEmpty()) {
            logger.debug("工作流执行完成，无临时文件需要清理: flowId={}", context.getFlowId());
            return;
        }
        
        int deleted = 0;
        int failed = 0;
        for (String filePath : filesToDelete) {
            if (filePath == null || filePath.isEmpty()) {
                continue;
            }
            try {
                File file = new File(filePath);
                if (file.exists()) {
                    if (file.delete()) {
                        deleted++;
                        logger.debug("已删除临时文件: {}", filePath);
                    } else {
                        failed++;
                        logger.warn("删除临时文件失败: {}", filePath);
                    }
                }
            } catch (Exception e) {
                failed++;
                logger.warn("删除临时文件异常: {}", filePath, e);
            }
        }
        
        if (deleted > 0 || failed > 0) {
            logger.info("工作流执行完成，清理临时文件: flowId={}, 删除成功={}, 删除失败={}", 
                    context.getFlowId(), deleted, failed);
        }
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
                
                // 检查是否是异步节点
                boolean isAsync = handler.isAsync();
                logger.info("节点异步检查: type={}, nodeId={}, isAsync={}", node.getNodeType(), node.getNodeId(), isAsync);
                
                if (isAsync) {
                    // 异步节点：保存执行状态，执行节点后不继续后续节点，等待异步回调
                    context.setFlowDefinition(flow);
                    context.setCurrentNode(node);
                    context.setVisitedNodes(new HashSet<>(visiting));  // 创建副本
                    context.setCurrentDepth(depth);
                    context.setExecutor(this);
                    
                    // 执行异步节点（handler内部会安排回调）
                    handler.execute(node, context);
                    // 不继续执行后续节点，等待异步回调
                    logger.info("异步节点已提交，等待回调: nodeId={}, flowId={}", node.getNodeId(), flow.getFlowId());
                    return;
                } else {
                    // 同步节点：正常执行
                    success = handler.execute(node, context);
                }
            } catch (Exception e) {
                logger.error("执行节点失败: nodeId={}", node.getNodeId(), e);
                success = false;
            }
        }

        // 如果节点执行失败（success=false），则不继续执行后续节点（确保同步执行）
        if (!success) {
            logger.info("节点执行失败，终止工作流执行: nodeId={}, nodeType={}, flowId={}", 
                    node.getNodeId(), node.getNodeType(), flow.getFlowId());
            return;
        }
        
        List<FlowNodeDefinition> nextNodes = flow.getNextNodes(node.getNodeId(), success, context);
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
            List<FlowContext> branchContexts = new ArrayList<>();
            
            for (FlowNodeDefinition next : nextNodes) {
                Set<String> nextVisiting = new HashSet<>(visiting);
                // 每个分支创建独立的context副本，避免并发修改
                FlowContext branchContext = copyContext(context);
                branchContexts.add(branchContext);
                
                Future<?> future = branchExecutor.submit(() -> {
                    try {
                        executeNode(flow, branchContext, next, nextVisiting, depth + 1);
                    } catch (Exception e) {
                        logger.error("分支执行异常: nodeId={}", next.getNodeId(), e);
                    }
                });
                futures.add(future);
            }
            
            // 等待所有分支完成
            for (Future<?> future : futures) {
                try {
                    future.get();  // 等待分支完成
                } catch (Exception e) {
                    logger.error("等待分支完成异常", e);
                }
            }
            
            // 收集所有分支context中的临时文件路径到主context，以便最后统一清理
            for (FlowContext branchContext : branchContexts) {
                collectTempFilePaths(branchContext, context);
            }
        }
    }
    
    /**
     * 从分支context收集临时文件路径到主context
     */
    private void collectTempFilePaths(FlowContext branchContext, FlowContext mainContext) {
        Map<String, Object> branchVars = branchContext.getVariables();
        if (branchVars == null) return;
        
        // 收集抓图路径
        Object capturePath = branchVars.get("capturePath");
        if (capturePath instanceof String) {
            addToTempFileList(mainContext, (String) capturePath);
        }
        
        // 收集录像路径
        Object recordFilePath = branchVars.get("recordFilePath");
        if (recordFilePath instanceof String) {
            addToTempFileList(mainContext, (String) recordFilePath);
        }
    }
    
    /**
     * 添加临时文件路径到清理列表
     */
    @SuppressWarnings("unchecked")
    private void addToTempFileList(FlowContext context, String filePath) {
        if (filePath == null || filePath.isEmpty()) return;
        
        Object existingList = context.getVariables().get("_tempFilesToClean");
        List<String> tempFiles;
        if (existingList instanceof List) {
            tempFiles = (List<String>) existingList;
        } else {
            tempFiles = new ArrayList<>();
            context.putVariable("_tempFilesToClean", tempFiles);
        }
        
        if (!tempFiles.contains(filePath)) {
            tempFiles.add(filePath);
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
    
    /**
     * 从异步节点回调继续执行后续节点
     * @param context 流程上下文（包含保存的执行状态）
     * @param success 异步操作是否成功
     */
    public void continueFromAsync(FlowContext context, boolean success) {
        FlowDefinition flow = context.getFlowDefinition();
        FlowNodeDefinition node = context.getCurrentNode();
        Set<String> visiting = context.getVisitedNodes();
        int depth = context.getCurrentDepth();
        
        if (flow == null || node == null) {
            logger.error("异步回调状态不完整，无法继续执行: flowId={}", context.getFlowId());
            return;
        }
        
        logger.info("异步节点回调: nodeId={}, success={}, flowId={}, deviceId={}, 继续执行后续节点", 
                node.getNodeId(), success, flow.getFlowId(), context.getDeviceId());
        
        // 如果节点执行失败（success=false），且是事件触发器节点，则不继续执行后续节点
        if (!success && "event_trigger".equalsIgnoreCase(node.getNodeType())) {
            logger.info("事件触发器返回false，终止工作流执行: nodeId={}, flowId={}", node.getNodeId(), flow.getFlowId());
            return;
        }
        
        // 如果节点执行失败（success=false），则不继续执行后续节点（确保同步执行）
        // 注意：即使工作流配置了failure分支，也不应该继续执行，因为节点失败意味着无法完成预期功能
        if (!success) {
            logger.info("节点执行失败，终止工作流执行: nodeId={}, nodeType={}, flowId={}", 
                    node.getNodeId(), node.getNodeType(), flow.getFlowId());
            return;
        }
        
        // 获取后续节点（只有在success=true时才会执行到这里）
        List<FlowNodeDefinition> nextNodes = flow.getNextNodes(node.getNodeId(), success, context);
        if (nextNodes == null || nextNodes.isEmpty()) {
            logger.debug("异步节点后续无节点，流程分支结束: nodeId={}", node.getNodeId());
            return;
        }

        // 继续执行后续节点
        if (nextNodes.size() == 1) {
            // 只有一个后续节点，直接顺序执行
            Set<String> nextVisiting = new HashSet<>(visiting);
            executeNode(flow, context, nextNodes.get(0), nextVisiting, depth + 1);
        } else {
            // 多个后续节点，并行执行各分支
            logger.info("检测到多个分支({}个)，并行执行", nextNodes.size());
            List<Future<?>> futures = new ArrayList<>();
            List<FlowContext> branchContexts = new ArrayList<>();
            
            for (FlowNodeDefinition next : nextNodes) {
                Set<String> nextVisiting = new HashSet<>(visiting);
                // 每个分支创建独立的context副本，避免并发修改
                FlowContext branchContext = copyContext(context);
                branchContexts.add(branchContext);
                
                Future<?> future = branchExecutor.submit(() -> {
                    try {
                        executeNode(flow, branchContext, next, nextVisiting, depth + 1);
                    } catch (Exception e) {
                        logger.error("分支执行异常: nodeId={}", next.getNodeId(), e);
                    }
                });
                futures.add(future);
            }
            
            // 等待所有分支完成
            for (Future<?> future : futures) {
                try {
                    future.get();  // 等待分支完成
                } catch (Exception e) {
                    logger.error("等待分支完成异常", e);
                }
            }
            
            // 收集所有分支context中的临时文件路径到主context，以便最后统一清理
            for (FlowContext branchContext : branchContexts) {
                collectTempFilePaths(branchContext, context);
            }
        }
    }
}
