package com.digital.video.gateway.workflow;

/**
 * 节点执行处理器接口
 */
public interface FlowNodeHandler {
    /**
     * 执行节点逻辑
     * @return true 表示成功，false 表示失败
     */
    boolean execute(FlowNodeDefinition node, FlowContext context) throws Exception;
}
