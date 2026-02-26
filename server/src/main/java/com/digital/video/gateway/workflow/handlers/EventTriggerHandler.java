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
        if (cfg != null) {
            logger.debug("事件触发器节点配置: {}", cfg);
            Object debounceObj = cfg.get("debounceSeconds");
            if (debounceObj instanceof Number) {
                debounceSeconds = ((Number) debounceObj).longValue();
                logger.debug("从节点配置读取防抖间隔: {}秒", debounceSeconds);
            } else if (debounceObj != null) {
                logger.warn("防抖间隔配置类型错误，期望Number，实际: {}", debounceObj.getClass().getName());
            }
        } else {
            logger.debug("节点配置为空，使用默认防抖间隔: {}秒", debounceSeconds);
        }
        
        String deviceId = context.getDeviceId();
        // 优先使用eventKey（标准事件键），兼容alarmType（旧格式）
        String eventKey = (String) context.getVariables().get("eventKey");
        String alarmType = eventKey != null ? eventKey : (String) context.getVariables().get("alarmType");
        if (alarmType == null) {
            alarmType = context.getAlarmType(); // 回退到context的alarmType字段
        }
        String flowId = context.getFlowId();
        
        if (deviceId == null) {
            logger.warn("事件触发器: 缺少deviceId");
            return false;
        }
        
        // 检查是否有事件类型过滤（支持eventKeys和alarmTypes两种配置）
        if (cfg != null) {
            // 优先检查eventKeys（标准事件键列表）
            Object eventKeysObj = cfg.get("eventKeys");
            if (eventKeysObj instanceof java.util.List) {
                @SuppressWarnings("unchecked")
                java.util.List<String> allowedEventKeys = (java.util.List<String>) eventKeysObj;
                if (!allowedEventKeys.isEmpty() && !allowedEventKeys.contains(eventKey != null ? eventKey : alarmType)) {
                    logger.debug("事件触发器: 事件键 {} 不在监听列表中，跳过", eventKey != null ? eventKey : alarmType);
                    return false;
                }
            }
            
            // 兼容：检查alarmTypes（旧配置）
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
        // 防抖key：优先使用eventKey（标准事件键），如果没有则使用alarmType
        // 如果alarmType为null，则只按flowId和deviceId防抖（同一设备的所有报警类型共享防抖）
        String debounceKey;
        String debounceEventKey = eventKey != null ? eventKey : alarmType;
        if (debounceEventKey != null && !debounceEventKey.isEmpty()) {
            debounceKey = flowId + ":" + deviceId + ":" + debounceEventKey;
        } else {
            // alarmType为null时，按设备防抖（所有报警类型共享）
            debounceKey = flowId + ":" + deviceId;
        }
        
        long now = System.currentTimeMillis();
        Long lastTime = lastTriggerTime.get(debounceKey);
        
        if (lastTime != null) {
            long elapsedSeconds = (now - lastTime) / 1000;
            if (elapsedSeconds < debounceSeconds) {
                return false;  // 被防抖间隔过滤，不执行、不打日志
            }
        }
        
        // 更新触发时间
        lastTriggerTime.put(debounceKey, now);
        
        logger.info("事件触发器通过: flowId={}, deviceId={}, eventKey={}, alarmType={}, 防抖间隔={}秒, 防抖key={}",
                flowId, deviceId, eventKey, alarmType, debounceSeconds, debounceKey);
        
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
