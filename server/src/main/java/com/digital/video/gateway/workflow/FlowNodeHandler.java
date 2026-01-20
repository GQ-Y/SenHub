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
    
    /**
     * 是否是异步节点
     * 异步节点执行后不会立即继续后续节点，而是等待异步回调
     * @return true 表示异步节点，false 表示同步节点（默认）
     */
    default boolean isAsync() {
        return false;
    }
}
