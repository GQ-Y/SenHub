package com.digital.video.gateway.service;

import com.digital.video.gateway.device.DeviceManager;
import com.digital.video.gateway.database.AlarmRecord;
import com.digital.video.gateway.database.AlarmRule;
import com.digital.video.gateway.database.DeviceInfo;
import com.digital.video.gateway.database.RecordingTask;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 报警服务
 * 处理设备报警信号，根据规则执行动作
 */
public class AlarmService {
    private static final Logger logger = LoggerFactory.getLogger(AlarmService.class);
    
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
        
        logger.info("收到报警信号: deviceId={}, channel={}, alarmType={}, message={}", 
            deviceId, channel, alarmType, alarmMessage);
        
        try {
            // 获取设备信息
            DeviceInfo device = deviceManager.getDevice(deviceId);
            if (device == null) {
                logger.warn("设备不存在，无法处理报警: {}", deviceId);
                return;
            }
            
            // 确保设备在线
            if (!"online".equals(device.getStatus())) {
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
            
            // 执行规则动作
            if (!matchedRules.isEmpty()) {
                for (AlarmRule rule : matchedRules) {
                    try {
                        Map<String, Object> actions = objectMapper.readValue(rule.getActions(), new TypeReference<Map<String, Object>>() {});
                        
                        // 抓拍
                        if (Boolean.TRUE.equals(actions.get("capture"))) {
                            int actualChannel = channel > 0 ? channel : device.getChannel();
                            String capturePath = captureService.captureSnapshot(deviceId, actualChannel);
                            if (capturePath != null) {
                                logger.info("报警自动抓图成功: deviceId={}, channel={}, filePath={}", 
                                    deviceId, actualChannel, capturePath);
                                
                                // 上传到OSS
                                if (Boolean.TRUE.equals(actions.get("upload")) && ossService != null && ossService.isEnabled()) {
                                    try {
                                        String ossPath = "alarm/" + deviceId + "/" + 
                                            new SimpleDateFormat("yyyyMMdd").format(new Date()) + 
                                            "/" + new File(capturePath).getName();
                                        captureUrl = ossService.uploadFile(capturePath, ossPath);
                                        if (captureUrl != null) {
                                            logger.info("报警抓图已上传到OSS: deviceId={}, ossUrl={}", deviceId, captureUrl);
                                        }
                                    } catch (Exception e) {
                                        logger.error("上传报警抓图到OSS异常: deviceId={}", deviceId, e);
                                    }
                                }
                            }
                        }
                        
                        // 录像
                        if (Boolean.TRUE.equals(actions.get("record"))) {
                            if (recordingTaskService != null) {
                                try {
                                    // 计算报警时间前后的时间段（默认前后各30秒）
                                    long currentTime = System.currentTimeMillis();
                                    long beforeSeconds = 30;
                                    long afterSeconds = 30;
                                    
                                    // 从规则条件中获取录像时长配置（如果有）
                                    if (rule.getConditions() != null && !rule.getConditions().isEmpty()) {
                                        try {
                                            Map<String, Object> conditions = objectMapper.readValue(rule.getConditions(), new TypeReference<Map<String, Object>>() {});
                                            if (conditions.containsKey("recordBeforeSeconds")) {
                                                beforeSeconds = ((Number) conditions.get("recordBeforeSeconds")).longValue();
                                            }
                                            if (conditions.containsKey("recordAfterSeconds")) {
                                                afterSeconds = ((Number) conditions.get("recordAfterSeconds")).longValue();
                                            }
                                        } catch (Exception e) {
                                            logger.warn("解析规则条件失败，使用默认录像时长", e);
                                        }
                                    }
                                    
                                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                                    String startTime = sdf.format(new java.util.Date(currentTime - beforeSeconds * 1000));
                                    String endTime = sdf.format(new java.util.Date(currentTime + afterSeconds * 1000));
                                    
                                    int actualChannel = channel > 0 ? channel : device.getChannel();
                                    RecordingTask task = recordingTaskService.downloadRecording(deviceId, actualChannel, startTime, endTime);
                                    if (task != null) {
                                        logger.info("创建报警录像任务成功: deviceId={}, taskId={}, startTime={}, endTime={}", 
                                            deviceId, task.getTaskId(), startTime, endTime);
                                        
                                        // 如果启用了OSS上传，等待录像完成后上传
                                        if (Boolean.TRUE.equals(actions.get("upload")) && ossService != null && ossService.isEnabled()) {
                                            // 这里可以启动一个后台任务来监控录像任务完成并上传
                                            // TODO: 实现录像任务完成后的OSS上传逻辑
                                            logger.info("录像任务创建成功，等待完成后上传OSS: taskId={}", task.getTaskId());
                                        }
                                    }
                                } catch (Exception e) {
                                    logger.error("创建报警录像任务失败: deviceId={}", deviceId, e);
                                }
                            } else {
                                logger.warn("录像任务服务未初始化，无法创建录像任务: deviceId={}", deviceId);
                            }
                        }
                        
                        // MQTT上报
                        if (Boolean.TRUE.equals(actions.get("mqtt")) && mqttClient != null) {
                            try {
                                Map<String, Object> mqttMessage = new HashMap<>();
                                mqttMessage.put("alarmId", alarmRecord.getAlarmId());
                                mqttMessage.put("deviceId", deviceId);
                                mqttMessage.put("assemblyId", assemblyId);
                                mqttMessage.put("alarmType", alarmType);
                                mqttMessage.put("alarmLevel", alarmRecord.getAlarmLevel());
                                mqttMessage.put("channel", channel);
                                mqttMessage.put("captureUrl", captureUrl);
                                mqttMessage.put("videoUrl", videoUrl);
                                mqttMessage.put("timestamp", System.currentTimeMillis());
                                
                                String topic = "alarm/report/" + deviceId;
                                mqttClient.publish(topic, objectMapper.writeValueAsString(mqttMessage));
                                alarmRecord.setMqttSent(true);
                                logger.info("报警MQTT上报成功: deviceId={}, topic={}", deviceId, topic);
                            } catch (Exception e) {
                                logger.error("报警MQTT上报失败: deviceId={}", deviceId, e);
                            }
                        }
                        
                        // 音柱播报
                        if (Boolean.TRUE.equals(actions.get("speaker")) && speakerService != null) {
                            // 查找装置中的音柱设备
                            if (assemblyId != null && database != null) {
                                com.digital.video.gateway.service.AssemblyService assemblyService = 
                                    new com.digital.video.gateway.service.AssemblyService(database);
                                List<com.digital.video.gateway.database.AssemblyDevice> devices = assemblyService.getAssemblyDevices(assemblyId);
                                for (com.digital.video.gateway.database.AssemblyDevice ad : devices) {
                                    if ("speaker".equals(ad.getDeviceRole())) {
                                        speakerService.playVoice(ad.getDeviceId(), "检测到" + alarmType + "报警");
                                        alarmRecord.setSpeakerTriggered(true);
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        logger.error("执行规则动作失败: ruleId={}", rule.getRuleId(), e);
                    }
                }
            } else {
                // 没有匹配的规则，执行默认动作（抓图）
                int actualChannel = channel > 0 ? channel : device.getChannel();
                String capturePath = captureService.captureSnapshot(deviceId, actualChannel);
                if (capturePath != null && ossService != null && ossService.isEnabled()) {
                    try {
                        String ossPath = "alarm/" + deviceId + "/" + 
                            new SimpleDateFormat("yyyyMMdd").format(new Date()) + 
                            "/" + new File(capturePath).getName();
                        captureUrl = ossService.uploadFile(capturePath, ossPath);
                    } catch (Exception e) {
                        logger.error("上传报警抓图到OSS异常: deviceId={}", deviceId, e);
                    }
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
}
