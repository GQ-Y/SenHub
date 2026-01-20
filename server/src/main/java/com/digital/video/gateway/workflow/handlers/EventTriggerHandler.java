package com.digital.video.gateway.workflow.handlers;

import com.digital.video.gateway.workflow.FlowContext;
import com.digital.video.gateway.workflow.FlowNodeDefinition;
import com.digital.video.gateway.workflow.FlowNodeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 事件触发器节点处理器
 * 
 * 支持配置：
 * - debounceSeconds: 防抖间隔（秒），同一设备同一报警类型在此时间内只触发一次
 * - alarmTypes: 要监听的报警类型列表（可选，为空则监听所有类型）
 * 
 * 示例配置：
 * {
 *   "debounceSeconds": 30,
 *   "alarmTypes": ["MOTION_DETECTION", "ALARM_V30"]
 * }
 */
public class EventTriggerHandler implements FlowNodeHandler {
    private static final Logger logger = LoggerFactory.getLogger(EventTriggerHandler.class);
    
    // 防抖记录：key = flowId:deviceId:alarmType, value = 上次触发时间戳
    private static final ConcurrentHashMap<String, Long> lastTriggerTime = new ConcurrentHashMap<>();
    
    // 默认防抖间隔（秒）
    private static final long DEFAULT_DEBOUNCE_SECONDS = 5;

    @Override
    public boolean execute(FlowNodeDefinition node, FlowContext context) throws Exception {
        Map<String, Object> cfg = node.getConfig();
        
        // 获取防抖间隔配置
        long debounceSeconds = DEFAULT_DEBOUNCE_SECONDS;
        if (cfg != null && cfg.get("debounceSeconds") instanceof Number) {
            debounceSeconds = ((Number) cfg.get("debounceSeconds")).longValue();
        }
        
        String deviceId = context.getDeviceId();
        String alarmType = (String) context.getVariables().get("alarmType");
        String flowId = context.getFlowId();
        
        if (deviceId == null) {
            logger.warn("事件触发器: 缺少deviceId");
            return false;
        }
        
        // 检查是否有报警类型过滤
        if (cfg != null && cfg.get("alarmTypes") != null) {
            Object alarmTypesObj = cfg.get("alarmTypes");
            if (alarmTypesObj instanceof java.util.List) {
                @SuppressWarnings("unchecked")
                java.util.List<String> allowedTypes = (java.util.List<String>) alarmTypesObj;
                if (!allowedTypes.isEmpty() && !allowedTypes.contains(alarmType)) {
                    logger.debug("事件触发器: 报警类型 {} 不在监听列表中，跳过", alarmType);
                    return false;
                }
            }
        }
        
        // 防抖检查
        String debounceKey = flowId + ":" + deviceId + ":" + alarmType;
        long now = System.currentTimeMillis();
        Long lastTime = lastTriggerTime.get(debounceKey);
        
        if (lastTime != null) {
            long elapsedSeconds = (now - lastTime) / 1000;
            if (elapsedSeconds < debounceSeconds) {
                logger.info("事件触发器防抖: flowId={}, deviceId={}, alarmType={}, 距上次触发{}秒, 防抖间隔{}秒, 跳过执行",
                        flowId, deviceId, alarmType, elapsedSeconds, debounceSeconds);
                return false;  // 返回false阻止后续节点执行
            }
        }
        
        // 更新触发时间
        lastTriggerTime.put(debounceKey, now);
        
        logger.info("事件触发器通过: flowId={}, deviceId={}, alarmType={}, 防抖间隔={}秒",
                flowId, deviceId, alarmType, debounceSeconds);
        
        return true;
    }
    
    /**
     * 清理过期的防抖记录（可选，定期调用）
     */
    public static void cleanupExpiredEntries(long maxAgeSeconds) {
        long now = System.currentTimeMillis();
        long threshold = now - maxAgeSeconds * 1000;
        lastTriggerTime.entrySet().removeIf(entry -> entry.getValue() < threshold);
    }
}
