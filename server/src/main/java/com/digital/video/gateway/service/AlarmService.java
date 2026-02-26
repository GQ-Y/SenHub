package com.digital.video.gateway.service;

import com.digital.video.gateway.device.DeviceManager;
import com.digital.video.gateway.database.AlarmRecord;
import com.digital.video.gateway.database.AlarmRule;
import com.digital.video.gateway.database.CanonicalEventTable;
import com.digital.video.gateway.database.DeviceInfo;
import com.digital.video.gateway.database.RecordingTask;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.digital.video.gateway.database.AlarmFlow;
import com.digital.video.gateway.workflow.FlowContext;
import com.digital.video.gateway.workflow.FlowDefinition;
import com.digital.video.gateway.workflow.FlowExecutor;
import com.digital.video.gateway.workflow.FlowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Connection;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 报警服务
 * 处理设备报警信号，根据规则执行动作
 */
public class AlarmService {
    private static final Logger logger = LoggerFactory.getLogger(AlarmService.class);
    
    // 报警防抖：记录每个设备最后一次报警处理时间，防止频繁触发
    // key: deviceId:alarmType, value: 上次处理时间戳
    private final ConcurrentHashMap<String, Long> lastAlarmTime = new ConcurrentHashMap<>();
    // 报警防抖间隔（毫秒）- 同一设备同一报警类型在此时间内只处理一次，可配置
    private long alarmDebounceIntervalMs = 5000; // 默认5秒
    
    private final DeviceManager deviceManager;
    private final CaptureService captureService;
    private final OssService ossService;
    private AlarmRuleService alarmRuleService;
    private AlarmRecordService alarmRecordService;
    private RecorderService recorderService;
    private RecordingTaskService recordingTaskService;
    private SpeakerService speakerService;
    private PTZService ptzService;
    private com.digital.video.gateway.mqtt.MqttPublisher mqttPublisher;
    private FlowService flowService;
    private FlowExecutor flowExecutor;
    private boolean enabled = true;
    private final ObjectMapper objectMapper = new ObjectMapper();
    /** 报警工作流专用线程池，避免在 SDK 回调线程上同步执行工作流导致阻塞 */
    private final ExecutorService alarmWorkflowExecutor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "AlarmWorkflow");
        t.setDaemon(false);
        return t;
    });
    
    public AlarmService(DeviceManager deviceManager, CaptureService captureService, OssService ossService) {
        this.deviceManager = deviceManager;
        this.captureService = captureService;
        this.ossService = ossService;
    }
    
    /**
     * 设置规则服务
     */
    public void setAlarmRuleService(AlarmRuleService alarmRuleService) {
        this.alarmRuleService = alarmRuleService;
    }
    
    /**
     * 设置报警记录服务
     */
    public void setAlarmRecordService(AlarmRecordService alarmRecordService) {
        this.alarmRecordService = alarmRecordService;
    }
    
    /**
     * 设置录像服务
     */
    public void setRecorderService(RecorderService recorderService) {
        this.recorderService = recorderService;
    }
    
    /**
     * 设置录像任务服务
     */
    public void setRecordingTaskService(RecordingTaskService recordingTaskService) {
        this.recordingTaskService = recordingTaskService;
    }
    
    /**
     * 设置音柱服务
     */
    public void setSpeakerService(SpeakerService speakerService) {
        this.speakerService = speakerService;
    }
    
    /**
     * 设置PTZ服务
     */
    public void setPTZService(PTZService ptzService) {
        this.ptzService = ptzService;
    }
    
    /**
     * 设置MQTT客户端
     */
    public void setMqttPublisher(com.digital.video.gateway.mqtt.MqttPublisher mqttPublisher) {
        this.mqttPublisher = mqttPublisher;
    }

    /**
    * 设置流程服务
    */
    public void setFlowService(FlowService flowService) {
        this.flowService = flowService;
    }

    /**
     * 设置流程执行器
     */
    public void setFlowExecutor(FlowExecutor flowExecutor) {
        this.flowExecutor = flowExecutor;
    }
    
    /**
     * 设置报警防抖间隔（秒）
     * 同一设备同一类型报警在此时间内只处理一次
     */
    public void setAlarmDebounceIntervalSeconds(long seconds) {
        this.alarmDebounceIntervalMs = seconds * 1000;
        logger.info("报警防抖间隔已设置为: {}秒", seconds);
    }
    
    /**
     * 获取当前报警防抖间隔（秒）
     */
    public long getAlarmDebounceIntervalSeconds() {
        return alarmDebounceIntervalMs / 1000;
    }
    
    /**
     * 设置是否启用报警自动抓图
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        logger.info("报警自动抓图功能已{}", enabled ? "启用" : "禁用");
    }
    
    /**
     * 处理报警事件
     * @param deviceId 设备ID
     * @param channel 通道号
     * @param alarmType 报警类型
     * @param alarmMessage 报警消息
     */
    public void handleAlarm(String deviceId, int channel, String alarmType, String alarmMessage) {
        handleAlarm(deviceId, channel, alarmType, alarmMessage, null);
    }
    
    /**
     * 处理报警事件（增强版）
     * @param deviceId 设备ID
     * @param channel 通道号
     * @param alarmType 报警类型
     * @param alarmMessage 报警消息
     * @param alarmData 报警数据（JSON格式）
     */
    public void handleAlarm(String deviceId, int channel, String alarmType, String alarmMessage, Map<String, Object> alarmData) {
        if (!enabled) {
            logger.debug("报警功能已禁用，跳过处理: deviceId={}, alarmType={}", deviceId, alarmType);
            return;
        }
        
        // 使用EventResolver解析标准事件（兼容旧alarmType字符串格式）
        String eventKey = null;
        String eventNameZh = null;
        String eventNameEn = null;
        String category = null;
        String alarmTypeDisplay = alarmType;
        String alarmTypeName = null; // 纯中文名称（不含原始alarmType）
        
        if (eventResolver != null) {
            try {
                EventResolver.ResolveResult result = eventResolver.resolveFromAlarmTypeString(alarmType);
                if (result != null) {
                    eventKey = result.getEventKey();
                    eventNameZh = result.getEventNameZh();
                    eventNameEn = result.getEventNameEn();
                    category = result.getCategory();
                    alarmTypeDisplay = eventNameZh + "(" + alarmType + ")";
                    alarmTypeName = eventNameZh;
                    logger.info("事件解析成功: alarmType={}, eventKey={}, eventNameZh={}", 
                            alarmType, eventKey, eventNameZh);
                } else {
                    logger.warn("事件解析失败，使用原始alarmType: alarmType={}", alarmType);
                }
            } catch (Exception e) {
                logger.error("事件解析异常: alarmType={}", alarmType, e);
            }
        } else {
            logger.warn("eventResolver为null，无法解析事件类型");
        }
        
        // 报警防抖检查：同一设备同一类型报警在防抖间隔内只处理一次（原子化，避免竞态）
        // 使用 eventKey（如果可用）作为防抖 key，确保相同标准事件不会重复触发
        String debounceKey = deviceId + ":" + (eventKey != null ? eventKey : alarmType);
        long now = System.currentTimeMillis();
        Long previousTime = lastAlarmTime.compute(debounceKey, (k, lastTime) -> {
            if (lastTime != null && (now - lastTime) < alarmDebounceIntervalMs) {
                return lastTime; // 仍在防抖期内，保留原值，不更新
            }
            return now; // 超过防抖期或首次，更新为当前时间
        });
        // 只有本次“通过防抖”时 compute 才返回 now，此时应处理；被防抖时返回的是旧的 lastTime（被防抖的不打日志）
        boolean shouldProcess = (previousTime != null && previousTime == now);
        if (!shouldProcess) {
            return;
        }
        
        logger.info("收到报警信号: deviceId={}, channel={}, alarmType={}, eventKey={}, message={}", 
            deviceId, channel, alarmTypeDisplay, eventKey, alarmMessage);
        
        // 工作流执行提交到专用线程池，避免阻塞 SDK 回调线程
        final String fDeviceId = deviceId;
        final int fChannel = channel;
        final String fAlarmType = alarmType;
        final String fAlarmMessage = alarmMessage;
        final Map<String, Object> fAlarmData = alarmData != null ? new HashMap<>(alarmData) : null;
        final String fEventKey = eventKey;
        final String fEventNameZh = eventNameZh;
        final String fEventNameEn = eventNameEn;
        final String fCategory = category;
        final String fAlarmTypeDisplay = alarmTypeDisplay;
        final String fAlarmTypeName = alarmTypeName;
        alarmWorkflowExecutor.submit(() -> runAlarmWorkflow(fDeviceId, fChannel, fAlarmType, fAlarmMessage, fAlarmData,
                fEventKey, fEventNameZh, fEventNameEn, fCategory, fAlarmTypeDisplay, fAlarmTypeName));
    }
    
    /**
     * 在报警工作流线程池中执行：获取设备、匹配规则、执行工作流、写报警记录（不阻塞 SDK 回调）
     */
    private void runAlarmWorkflow(String deviceId, int channel, String alarmType, String alarmMessage,
            Map<String, Object> alarmData, String eventKey, String eventNameZh, String eventNameEn, String category,
            String alarmTypeDisplay, String alarmTypeName) {
        try {
            // 获取设备信息
            DeviceInfo device = deviceManager.getDevice(deviceId);
            if (device == null) {
                logger.warn("设备不存在，无法处理报警: {}", deviceId);
                return;
            }
            
            // 确保设备在线 (status: 1=在线, 0=离线)
            if (device.getStatus() != 1) {
                logger.warn("设备不在线，无法处理报警: deviceId={}, status={}", deviceId, device.getStatus());
                return;
            }

            // 天地伟业：健康检查未通过前不处理该摄像头的任何事件
            if (DeviceInfo.BRAND_TIANDY.equalsIgnoreCase(device.getBrand())) {
                DeviceInfo.HealthStatus health = deviceManager.checkTiandyDeviceHealth(deviceId);
                if (health != DeviceInfo.HealthStatus.HEALTHY) {
                    logger.warn("天地伟业设备健康检查未通过，跳过报警处理: deviceId={}, 状态={}", deviceId, health);
                    return;
                }
            }
            
            // 获取装置ID（如果有）
            String assemblyId = null;
            if (alarmRuleService != null && database != null) {
                com.digital.video.gateway.service.AssemblyService assemblyService = 
                    new com.digital.video.gateway.service.AssemblyService(database);
                List<com.digital.video.gateway.database.Assembly> assemblies = assemblyService.getAssembliesByDevice(deviceId);
                if (!assemblies.isEmpty()) {
                    assemblyId = assemblies.get(0).getAssemblyId();
                }
            }
            
            // 匹配规则（使用eventKey，如果可用）
            List<AlarmRule> matchedRules = new ArrayList<>();
            if (alarmRuleService != null) {
                String matchKey = eventKey != null ? eventKey : alarmType;
                matchedRules = alarmRuleService.matchRules(deviceId, assemblyId, matchKey, alarmData, alarmTypeDisplay);
                logger.info("匹配到 {} 条规则: deviceId={}, eventKey={}, alarmType={}", 
                        matchedRules.size(), deviceId, eventKey, alarmTypeDisplay);
            }
            
            AlarmRecord alarmRecord = new AlarmRecord();
            alarmRecord.setDeviceId(deviceId);
            alarmRecord.setAssemblyId(assemblyId);
            alarmRecord.setAlarmType(eventKey != null ? eventKey : alarmType);
            alarmRecord.setAlarmLevel("warning");
            alarmRecord.setChannel(channel);
            if (alarmData != null) {
                try {
                    alarmRecord.setAlarmData(objectMapper.writeValueAsString(alarmData));
                } catch (Exception e) {
                    logger.error("序列化报警数据失败", e);
                }
            }
            alarmRecord.setStatus("pending");
            
            String captureUrl = null;
            String videoUrl = null;
            FlowContext baseContext = buildFlowContext(deviceId, assemblyId, eventKey != null ? eventKey : alarmType, 
                    alarmMessage, channel, alarmData);
            if (eventKey != null) {
                baseContext.putVariable("eventKey", eventKey);
                baseContext.putVariable("eventNameZh", eventNameZh);
                baseContext.putVariable("eventNameEn", eventNameEn);
                baseContext.putVariable("category", category);
                baseContext.putVariable("originalAlarmType", alarmType);
                logger.info("已设置标准事件信息到context: eventKey={}, eventNameZh={}, originalAlarmType={}", 
                        eventKey, eventNameZh, alarmType);
            }
            if (alarmTypeName != null) {
                baseContext.putVariable("alarmTypeName", alarmTypeName);
                logger.info("已设置alarmTypeName到context: {} (原始: {})", alarmTypeName, alarmType);
            }
            
            Set<String> executedFlows = new HashSet<>();
            if (!matchedRules.isEmpty()) {
                for (int i = 0; i < matchedRules.size(); i++) {
                    AlarmRule rule = matchedRules.get(i);
                    String flowId = rule.getFlowId();
                    if (flowId == null || flowId.isEmpty()) {
                        logger.warn("规则未关联流程: ruleId={}, ruleName={}", rule.getRuleId(), rule.getName());
                        continue;
                    }
                    if (executedFlows.contains(flowId)) {
                        logger.info("工作流已执行，跳过重复: flowId={}, 被跳过规则={} (scope={})", 
                                flowId, rule.getName(), rule.getScope());
                        continue;
                    }
                    logger.info("执行规则[{}]: {} (scope={}, flowId={})", i + 1, rule.getName(), rule.getScope(), flowId);
                    FlowContext ctx = cloneFlowContext(baseContext);
                    boolean flowExecuted = executeFlowIfAvailable(rule, ctx);
                    if (flowExecuted) {
                        executedFlows.add(flowId);
                        captureUrl = firstNonNullUrl(captureUrl, ctx);
                        videoUrl = firstVideoUrl(videoUrl, ctx);
                    } else {
                        logger.warn("规则执行流程失败: ruleId={}", rule.getRuleId());
                    }
                }
            } else {
                logger.info("无匹配规则，跳过工作流执行: deviceId={}, alarmType={}", deviceId, alarmTypeDisplay);
            }
            
            alarmRecord.setCaptureUrl(captureUrl);
            alarmRecord.setVideoUrl(videoUrl);
            alarmRecord.setStatus("processed");
            // 报警统计只统计：触发了规则且至少执行了一个工作流（未被同事件防抖过滤）的报警
            if (!executedFlows.isEmpty() && alarmRecordService != null) {
                alarmRecordService.createAlarmRecord(alarmRecord);
            }
        } catch (Exception e) {
            logger.error("处理报警事件异常: deviceId={}, alarmType={}", deviceId, alarmType, e);
        }
    }
    
    /**
     * 处理雷达点云报警，驱动PTZ控制
     */
    public void handleRadarPointcloudAlarm(String deviceId, Map<String, Object> pointcloudData) {
        logger.info("处理雷达点云报警: deviceId={}", deviceId);
        
        try {
            // 解析点云数据中的位置信息
            Double x = pointcloudData != null ? (Double) pointcloudData.get("x") : null;
            Double y = pointcloudData != null ? (Double) pointcloudData.get("y") : null;
            Double distance = pointcloudData != null ? (Double) pointcloudData.get("distance") : null;
            
            if (x == null || y == null) {
                logger.warn("雷达点云数据缺少位置信息: deviceId={}", deviceId);
                return;
            }
            
            // 根据位置判断应该控制哪个摄像头
            // TODO: 实现根据位置判断摄像头逻辑
            // 这里需要根据装置配置中的设备角色来判断
            
            // 调用PTZService控制摄像头
            if (ptzService != null) {
                // TODO: 实现PTZ控制逻辑
                logger.info("雷达点云驱动PTZ控制（待实现）: deviceId={}, x={}, y={}, distance={}", deviceId, x, y, distance);
            }
            
        } catch (Exception e) {
            logger.error("处理雷达点云报警异常: deviceId={}", deviceId, e);
        }
    }
    
    // 临时字段，用于访问database
    private com.digital.video.gateway.database.Database database;
    private EventResolver eventResolver;
    
    public void setDatabase(com.digital.video.gateway.database.Database database) {
        this.database = database;
        if (database != null) {
            this.eventResolver = new EventResolver(database);
        }
    }

    private FlowContext buildFlowContext(String deviceId, String assemblyId, String alarmType,
            String alarmMessage, int channel, Map<String, Object> alarmData) {
        FlowContext context = new FlowContext();
        context.setDeviceId(deviceId);
        context.setAssemblyId(assemblyId);
        context.setAlarmType(alarmType);
        
        // 将 alarmType 也放入 variables，供工作流节点使用
        if (alarmType != null) {
            context.putVariable("alarmType", alarmType);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("alarmMessage", alarmMessage);
        payload.put("channel", channel);
        if (alarmData != null) {
            payload.put("alarmData", alarmData);
        }
        // 报警事件以 CSV/canonical_events 的 event_id 为主，并推送 event_key、event_name_zh、event_name_en
        if (alarmType != null) {
            payload.put("event_key", alarmType);
            java.util.Map<String, Object> canonical = null;
            if (database != null) {
                canonical = CanonicalEventTable.getCanonicalEvent(database.getConnection(), alarmType);
            }
            if (canonical == null) {
                canonical = CanonicalEventTable.getEventIdAndNamesFromCsv(alarmType);
            }
            if (canonical != null) {
                Object eventId = canonical.get("eventId");
                if (eventId != null) {
                    payload.put("event_id", eventId);
                }
                String nameZh = canonical.get("nameZh") != null ? canonical.get("nameZh").toString() : null;
                String nameEn = canonical.get("nameEn") != null ? canonical.get("nameEn").toString() : null;
                payload.put("event_name_zh", nameZh != null ? nameZh : alarmType);
                payload.put("event_name_en", nameEn != null ? nameEn : alarmType);
            } else {
                payload.put("event_name_zh", alarmType);
                payload.put("event_name_en", alarmType);
            }
        }
        context.setPayload(payload);
        return context;
    }

    private FlowContext cloneFlowContext(FlowContext source) {
        FlowContext ctx = new FlowContext();
        ctx.setFlowId(source.getFlowId());
        ctx.setDeviceId(source.getDeviceId());
        ctx.setAssemblyId(source.getAssemblyId());
        ctx.setAlarmType(source.getAlarmType());
        ctx.setPayload(new HashMap<>(source.getPayload() != null ? source.getPayload() : new HashMap<>()));
        ctx.setVariables(new java.util.concurrent.ConcurrentHashMap<>(source.getVariables()));
        return ctx;
    }

    private boolean executeFlowIfAvailable(AlarmRule rule, FlowContext context) {
        if (flowService == null || flowExecutor == null || rule.getFlowId() == null || rule.getFlowId().isEmpty()) {
            return false;
        }
        try {
            AlarmFlow flow = flowService.getFlow(rule.getFlowId());
            if (flow == null) {
                logger.warn("未找到匹配的流程: flowId={}", rule.getFlowId());
                return false;
            }
            FlowDefinition definition = flowService.toDefinition(flow);
            if (definition == null) {
                logger.warn("流程定义解析失败: flowId={}", rule.getFlowId());
                return false;
            }
            
            // 在执行工作流之前，先检查event_trigger节点是否会被防抖
            // 如果会被防抖，则完全跳过不执行任何工作流
            context.setFlowId(rule.getFlowId());
            if (shouldSkipFlowDueToDebounce(definition, context)) {
                return false;
            }
            
            flowExecutor.execute(definition, context);
            
            // 记录工作流执行历史
            if (database != null) {
                database.recordWorkflowExecution(rule.getFlowId(), rule.getRuleId(), context.getDeviceId());
            }
            
            return true;
        } catch (Exception e) {
            logger.error("执行流程失败: flowId={}", rule.getFlowId(), e);
            return false;
        }
    }
    
    /**
     * 检查工作流是否应该因为事件触发器防抖而跳过
     * @param definition 工作流定义
     * @param context 流程上下文
     * @return 如果应该跳过则返回true
     */
    private boolean shouldSkipFlowDueToDebounce(FlowDefinition definition, FlowContext context) {
        try {
            // 查找工作流中的第一个event_trigger节点
            com.digital.video.gateway.workflow.FlowNodeDefinition startNode = definition.getStartNode();
            if (startNode == null) {
                return false;
            }
            
            // 如果第一个节点不是event_trigger，则不需要检查防抖
            if (!"event_trigger".equalsIgnoreCase(startNode.getNodeType())) {
                return false;
            }
            
            // 直接检查防抖状态，不调用execute方法（避免更新lastTriggerTime）
            // 使用反射访问EventTriggerHandler的私有防抖逻辑，或者直接复制防抖检查代码
            // 为了简化，我们直接访问EventTriggerHandler的静态防抖记录
            String deviceId = context.getDeviceId();
            String alarmType = (String) context.getVariables().get("alarmType");
            String flowId = context.getFlowId();
            
            if (deviceId == null) {
                return false;
            }
            
            // 构建防抖key（与EventTriggerHandler中的逻辑一致）
            String debounceKey;
            if (alarmType != null && !alarmType.isEmpty()) {
                debounceKey = flowId + ":" + deviceId + ":" + alarmType;
            } else {
                debounceKey = flowId + ":" + deviceId;
            }
            
            // 获取防抖间隔配置
            Map<String, Object> cfg = startNode.getConfig();
            long debounceSeconds = 5; // 默认值
            if (cfg != null) {
                Object debounceObj = cfg.get("debounceSeconds");
                if (debounceObj instanceof Number) {
                    debounceSeconds = ((Number) debounceObj).longValue();
                }
            }
            
            // 检查防抖状态（使用反射访问EventTriggerHandler的静态字段）
            try {
                java.lang.reflect.Field field = com.digital.video.gateway.workflow.handlers.EventTriggerHandler.class
                        .getDeclaredField("lastTriggerTime");
                field.setAccessible(true);
                @SuppressWarnings("unchecked")
                java.util.concurrent.ConcurrentHashMap<String, Long> lastTriggerTime = 
                        (java.util.concurrent.ConcurrentHashMap<String, Long>) field.get(null);
                
                Long lastTime = lastTriggerTime.get(debounceKey);
                if (lastTime != null) {
                    long now = System.currentTimeMillis();
                    long elapsedSeconds = (now - lastTime) / 1000;
                    if (elapsedSeconds < debounceSeconds) {
                        return true; // 被防抖，应该跳过（不打日志）
                    }
                }
            } catch (Exception e) {
                logger.warn("无法访问EventTriggerHandler的防抖记录，跳过防抖检查", e);
            }
            
            return false; // 未被防抖，可以执行
        } catch (Exception e) {
            logger.error("检查事件触发器防抖失败", e);
            return false; // 出错时不跳过
        }
    }

    private boolean executeDefaultFlow(FlowContext context) {
        if (flowService == null || flowExecutor == null) {
            return false;
        }
        try {
            List<AlarmFlow> flows = flowService.listFlows();
            if (flows == null || flows.isEmpty()) {
                return false;
            }
            AlarmFlow defaultFlow = flows.stream()
                    .filter(AlarmFlow::isDefault)
                    .findFirst()
                    .orElse(flows.get(0));
            FlowDefinition definition = flowService.toDefinition(defaultFlow);
            if (definition == null) {
                return false;
            }
            flowExecutor.execute(definition, context);
            return true;
        } catch (Exception e) {
            logger.error("执行默认流程失败", e);
            return false;
        }
    }

    private String firstNonNullUrl(String existing, FlowContext context) {
        if (existing != null) {
            return existing;
        }
        Object capture = context.getVariables().get("captureUrl");
        if (capture instanceof String) {
            return (String) capture;
        }
        Object oss = context.getVariables().get("ossUrl");
        if (oss instanceof String) {
            return (String) oss;
        }
        return null;
    }

    private String firstVideoUrl(String existing, FlowContext context) {
        if (existing != null) {
            return existing;
        }
        Object video = context.getVariables().get("videoUrl");
        if (video instanceof String) {
            return (String) video;
        }
        return null;
    }
}
