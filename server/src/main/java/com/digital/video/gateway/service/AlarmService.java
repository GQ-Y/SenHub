package com.digital.video.gateway.service;

import com.digital.video.gateway.device.DeviceManager;
import com.digital.video.gateway.database.AlarmRecord;
import com.digital.video.gateway.database.AlarmRule;
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
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 报警服务
 * 处理设备报警信号，根据规则执行动作
 */
public class AlarmService {
    private static final Logger logger = LoggerFactory.getLogger(AlarmService.class);
    
    // 报警防抖：记录每个设备最后一次报警处理时间，防止频繁触发
    // key: deviceId:alarmType, value: 上次处理时间戳
    private final ConcurrentHashMap<String, Long> lastAlarmTime = new ConcurrentHashMap<>();
    // 报警防抖间隔（毫秒）- 同一设备同一报警类型在此时间内只处理一次
    private static final long ALARM_DEBOUNCE_INTERVAL_MS = 5000; // 5秒
    
    private final DeviceManager deviceManager;
    private final CaptureService captureService;
    private final OssService ossService;
    private AlarmRuleService alarmRuleService;
    private AlarmRecordService alarmRecordService;
    private RecorderService recorderService;
    private RecordingTaskService recordingTaskService;
    private SpeakerService speakerService;
    private PTZService ptzService;
    private com.digital.video.gateway.mqtt.MqttClient mqttClient;
    private FlowService flowService;
    private FlowExecutor flowExecutor;
    private boolean enabled = true;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
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
    public void setMqttClient(com.digital.video.gateway.mqtt.MqttClient mqttClient) {
        this.mqttClient = mqttClient;
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
        
        // 报警防抖检查：同一设备同一类型报警在防抖间隔内只处理一次
        String debounceKey = deviceId + ":" + alarmType;
        long now = System.currentTimeMillis();
        Long lastTime = lastAlarmTime.get(debounceKey);
        if (lastTime != null && (now - lastTime) < ALARM_DEBOUNCE_INTERVAL_MS) {
            logger.debug("报警防抖，跳过处理: deviceId={}, alarmType={}, 距上次处理: {}ms", 
                    deviceId, alarmType, now - lastTime);
            return;
        }
        lastAlarmTime.put(debounceKey, now);
        
        logger.info("收到报警信号: deviceId={}, channel={}, alarmType={}, message={}", 
            deviceId, channel, alarmType, alarmMessage);
        
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
            
            // 获取装置ID（如果有）
            String assemblyId = null;
            if (alarmRuleService != null && database != null) {
                // 从装置设备关联表查询
                com.digital.video.gateway.service.AssemblyService assemblyService = 
                    new com.digital.video.gateway.service.AssemblyService(database);
                List<com.digital.video.gateway.database.Assembly> assemblies = assemblyService.getAssembliesByDevice(deviceId);
                if (!assemblies.isEmpty()) {
                    assemblyId = assemblies.get(0).getAssemblyId(); // 取第一个装置
                }
            }
            
            // 匹配规则
            List<AlarmRule> matchedRules = new ArrayList<>();
            if (alarmRuleService != null) {
                matchedRules = alarmRuleService.matchRules(deviceId, assemblyId, alarmType, alarmData);
                logger.info("匹配到 {} 条规则: deviceId={}, alarmType={}", matchedRules.size(), deviceId, alarmType);
            }
            
            // 创建报警记录
            AlarmRecord alarmRecord = new AlarmRecord();
            alarmRecord.setDeviceId(deviceId);
            alarmRecord.setAssemblyId(assemblyId);
            alarmRecord.setAlarmType(alarmType);
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
            FlowContext baseContext = buildFlowContext(deviceId, assemblyId, alarmType, alarmMessage, channel, alarmData);
            
            // 执行规则动作
            // 规则按优先级排序：设备级 > 装置级 > 全局
            // 去重：多条规则关联同一工作流时只执行一次（优先执行设备级规则的工作流）
            if (!matchedRules.isEmpty()) {
                Set<String> executedFlows = new HashSet<>();
                for (int i = 0; i < matchedRules.size(); i++) {
                    AlarmRule rule = matchedRules.get(i);
                    String flowId = rule.getFlowId();
                    if (flowId == null || flowId.isEmpty()) {
                        logger.warn("规则未关联流程: ruleId={}, ruleName={}", rule.getRuleId(), rule.getName());
                        continue;
                    }
                    
                    // 同一工作流只执行一次
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
                // 没有匹配的规则，尝试执行默认流程；如果失败则执行默认抓图动作
                FlowContext defaultContext = cloneFlowContext(baseContext);
                boolean defaultFlowExecuted = executeDefaultFlow(defaultContext);
                if (defaultFlowExecuted) {
                    captureUrl = firstNonNullUrl(captureUrl, defaultContext);
                    videoUrl = firstVideoUrl(videoUrl, defaultContext);
                } else {
                    logger.warn("无匹配规则且默认流程不存在，未执行任何报警处理");
                }
            }
            
            // 更新报警记录
            alarmRecord.setCaptureUrl(captureUrl);
            alarmRecord.setVideoUrl(videoUrl);
            alarmRecord.setStatus("processed");
            
            if (alarmRecordService != null) {
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
    
    public void setDatabase(com.digital.video.gateway.database.Database database) {
        this.database = database;
    }

    private FlowContext buildFlowContext(String deviceId, String assemblyId, String alarmType,
            String alarmMessage, int channel, Map<String, Object> alarmData) {
        FlowContext context = new FlowContext();
        context.setDeviceId(deviceId);
        context.setAssemblyId(assemblyId);
        context.setAlarmType(alarmType);

        Map<String, Object> payload = new HashMap<>();
        payload.put("alarmMessage", alarmMessage);
        payload.put("channel", channel);
        if (alarmData != null) {
            payload.put("alarmData", alarmData);
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
            flowExecutor.execute(definition, context);
            return true;
        } catch (Exception e) {
            logger.error("执行流程失败: flowId={}", rule.getFlowId(), e);
            return false;
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
