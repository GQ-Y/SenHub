package com.digital.video.gateway.service;

import com.digital.video.gateway.database.CanonicalEventTable;
import com.digital.video.gateway.database.Database;
import com.digital.video.gateway.tiandy.TiandySDK;
import com.digital.video.gateway.tiandy.TiandySDKStructure;
import com.digital.video.gateway.tiandy.NvssdkLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

/**
 * 统一事件解析器
 * 负责将SDK原始报警事件解析为标准事件键（event_key）
 */
public class EventResolver {
    private static final Logger logger = LoggerFactory.getLogger(EventResolver.class);
    
    private final Database database;
    
    public EventResolver(Database database) {
        this.database = database;
    }
    
    /**
     * 解析结果
     */
    public static class ResolveResult {
        private String eventKey;          // 标准事件键
        private String eventNameZh;       // 中文名称
        private String eventNameEn;       // 英文名称
        private String category;          // 分类
        private String originalAlarmType; // 原始报警类型（用于兼容和日志）
        private Map<String, Object> metadata; // 元数据（如ruleId, targetId等）
        
        public ResolveResult(String eventKey, String eventNameZh, String eventNameEn, String category, 
                            String originalAlarmType, Map<String, Object> metadata) {
            this.eventKey = eventKey;
            this.eventNameZh = eventNameZh;
            this.eventNameEn = eventNameEn;
            this.category = category;
            this.originalAlarmType = originalAlarmType;
            this.metadata = metadata != null ? metadata : new HashMap<>();
        }
        
        // Getters
        public String getEventKey() { return eventKey; }
        public String getEventNameZh() { return eventNameZh; }
        public String getEventNameEn() { return eventNameEn; }
        public String getCategory() { return category; }
        public String getOriginalAlarmType() { return originalAlarmType; }
        public Map<String, Object> getMetadata() { return metadata; }
    }
    
    /**
     * 解析天地伟业报警事件
     * @param iAlarmType 基础报警类型（0-10）
     * @param iChan 通道号
     * @param iAlarmState 报警状态
     * @param ulLogonID 登录句柄（用于二阶段解析）
     * @param nvssdkLibrary SDK库实例（用于二阶段解析）
     * @return 解析结果，如果解析失败返回null
     */
    public ResolveResult resolveTiandyEvent(int iAlarmType, int iChan, int iAlarmState, 
                                            int ulLogonID, NvssdkLibrary nvssdkLibrary) {
        try (Connection conn = database.getConnection()) {
            String sourceKind;
            int sourceCode;
            Map<String, Object> metadata = new HashMap<>();
            
            // 如果是智能分析报警（iAlarmType=6或9），需要二阶段解析获取iEventType
            if (iAlarmType == 6 || iAlarmType == 9) {
                int iEventType = -1;
                
                // 尝试获取智能分析详细信息
                if (nvssdkLibrary != null) {
                    try {
                        TiandySDKStructure.VcaTAlarmInfo vcaAlarmInfo = new TiandySDKStructure.VcaTAlarmInfo();
                        int result = nvssdkLibrary.NetClient_VCAGetAlarmInfo(ulLogonID, iChan, 
                                vcaAlarmInfo.getPointer(), vcaAlarmInfo.size());
                        
                        if (result == 0) {
                            vcaAlarmInfo.read();
                            iEventType = vcaAlarmInfo.iEventType;
                            metadata.put("ruleId", vcaAlarmInfo.iRuleID);
                            metadata.put("targetId", vcaAlarmInfo.uiTargetID);
                            logger.info("获取智能分析报警详细信息: logonID={}, channel={}, alarmType={}, eventType={}, ruleID={}, targetID={}", 
                                    ulLogonID, iChan, iAlarmType, iEventType, vcaAlarmInfo.iRuleID, vcaAlarmInfo.uiTargetID);
                        } else {
                            logger.warn("获取智能分析报警信息失败: logonID={}, channel={}, alarmType={}, result={}, 尝试使用iAlarmState作为索引", 
                                    ulLogonID, iChan, iAlarmType, result);
                            
                            // 回退：尝试使用iAlarmState作为索引
                            vcaAlarmInfo = new TiandySDKStructure.VcaTAlarmInfo();
                            result = nvssdkLibrary.NetClient_VCAGetAlarmInfo(ulLogonID, iAlarmState, 
                                    vcaAlarmInfo.getPointer(), vcaAlarmInfo.size());
                            if (result == 0) {
                                vcaAlarmInfo.read();
                                iEventType = vcaAlarmInfo.iEventType;
                                metadata.put("ruleId", vcaAlarmInfo.iRuleID);
                                metadata.put("targetId", vcaAlarmInfo.uiTargetID);
                                logger.info("使用iAlarmState作为索引成功获取智能分析报警信息: logonID={}, channel={}, alarmType={}, eventType={}, ruleID={}", 
                                        ulLogonID, iChan, iAlarmType, iEventType, vcaAlarmInfo.iRuleID);
                            } else {
                                logger.warn("使用iAlarmState作为索引也失败: logonID={}, channel={}, alarmType={}, result={}", 
                                        ulLogonID, iChan, iAlarmType, result);
                            }
                        }
                    } catch (Exception e) {
                        logger.error("调用NetClient_VCAGetAlarmInfo异常: logonID={}, channel={}, alarmType={}", 
                                ulLogonID, iChan, iAlarmType, e);
                    }
                } else {
                    logger.warn("nvssdkLibrary未初始化，无法获取智能分析报警详细信息: logonID={}, channel={}, alarmType={}", 
                            ulLogonID, iChan, iAlarmType);
                }
                
                // 如果成功获取到iEventType，使用vca_event映射
                if (iEventType >= 0) {
                    sourceKind = "vca_event";
                    sourceCode = iEventType;
                } else {
                    // 失败时降级：使用alarm_type映射
                    logger.warn("智能分析事件解析失败，降级使用基础报警类型: iAlarmType={}", iAlarmType);
                    sourceKind = "alarm_type";
                    sourceCode = iAlarmType;
                }
            } else {
                // 基础报警类型，直接使用iAlarmType
                sourceKind = "alarm_type";
                sourceCode = iAlarmType;
            }
            
            // 查询映射表
            Map<String, Object> event = CanonicalEventTable.resolveEvent(conn, "tiandy", sourceKind, sourceCode);
            if (event != null) {
                String originalAlarmType = "Tiandy_Alarm_" + sourceCode;
                return new ResolveResult(
                    (String) event.get("eventKey"),
                    (String) event.get("nameZh"),
                    (String) event.get("nameEn"),
                    (String) event.get("category"),
                    originalAlarmType,
                    metadata
                );
            } else {
                logger.warn("未找到天地伟业事件映射: sourceKind={}, sourceCode={}", sourceKind, sourceCode);
                return null;
            }
        } catch (Exception e) {
            logger.error("解析天地伟业事件失败: iAlarmType={}, iChan={}", iAlarmType, iChan, e);
            return null;
        }
    }
    
    /**
     * 解析海康报警事件
     * @param lCommand 报警命令类型（COMM_ALARM, COMM_ALARM_V30, COMM_ALARM_V40, COMM_ALARM_RULE, COMM_VCA_ALARM等）
     * @param dwAlarmType 报警类型（对于COMM_ALARM/V30/V40，表示基础报警类型）
     * @param wEventTypeEx 事件类型扩展（对于COMM_ALARM_RULE，表示行为分析事件类型）
     * @return 解析结果，如果解析失败返回null
     */
    public ResolveResult resolveHikvisionEvent(int lCommand, Integer dwAlarmType, Integer wEventTypeEx) {
        try (Connection conn = database.getConnection()) {
            String sourceKind;
            int sourceCode;
            
            // 根据lCommand确定source_kind和source_code
            if (lCommand == 0x1102) { // COMM_ALARM_RULE - 行为分析报警
                sourceKind = "vca_event";
                sourceCode = wEventTypeEx != null ? wEventTypeEx : -1;
            } else if (lCommand == 0x4993) { // COMM_VCA_ALARM - 智能检测通用报警
                sourceKind = "vca_alarm";
                sourceCode = lCommand;
            } else if (lCommand == 0x1100 || lCommand == 0x4000 || lCommand == 0x4007) { 
                // COMM_ALARM, COMM_ALARM_V30, COMM_ALARM_V40 - 基础报警
                sourceKind = "command";
                sourceCode = lCommand;
                // 如果有dwAlarmType，也可以尝试用alarm_type映射
                if (dwAlarmType != null && dwAlarmType >= 0) {
                    // 优先使用command映射，如果失败再尝试alarm_type
                    Map<String, Object> event = CanonicalEventTable.resolveEvent(conn, "hikvision", "command", lCommand);
                    if (event == null) {
                        sourceKind = "alarm_type";
                        sourceCode = dwAlarmType;
                    }
                }
            } else {
                // 其他命令类型
                sourceKind = "command";
                sourceCode = lCommand;
            }
            
            // 查询映射表
            Map<String, Object> event = CanonicalEventTable.resolveEvent(conn, "hikvision", sourceKind, sourceCode);
            if (event != null) {
                String originalAlarmType = "Hikvision_Alarm_" + Integer.toHexString(lCommand);
                return new ResolveResult(
                    (String) event.get("eventKey"),
                    (String) event.get("nameZh"),
                    (String) event.get("nameEn"),
                    (String) event.get("category"),
                    originalAlarmType,
                    new HashMap<>()
                );
            } else {
                logger.warn("未找到海康事件映射: sourceKind={}, sourceCode={}, lCommand=0x{}", 
                        sourceKind, sourceCode, Integer.toHexString(lCommand));
                return null;
            }
        } catch (Exception e) {
            logger.error("解析海康事件失败: lCommand=0x{}", Integer.toHexString(lCommand), e);
            return null;
        }
    }
    
    /**
     * 从旧的alarmType字符串解析（兼容旧代码）
     * 格式：Brand_Alarm_Code
     */
    public ResolveResult resolveFromAlarmTypeString(String alarmType) {
        if (alarmType == null || alarmType.isEmpty()) {
            return null;
        }
        
        String[] parts = alarmType.split("_");
        if (parts.length < 3 || !parts[1].equalsIgnoreCase("Alarm")) {
            // 非 Brand_Alarm_Code 格式，尝试按 event_key 直接查找
            return resolveByEventKey(alarmType);
        }
        
        String brand = parts[0].toLowerCase();
        try {
            int code = Integer.parseInt(parts[2]);
            
            try (Connection conn = database.getConnection()) {
                // 尝试多种source_kind
                String[] sourceKinds = {"vca_event", "alarm_type", "command"};
                for (String sourceKind : sourceKinds) {
                    Map<String, Object> event = CanonicalEventTable.resolveEvent(conn, brand, sourceKind, code);
                    if (event != null) {
                        return new ResolveResult(
                            (String) event.get("eventKey"),
                            (String) event.get("nameZh"),
                            (String) event.get("nameEn"),
                            (String) event.get("category"),
                            alarmType,
                            new HashMap<>()
                        );
                    }
                }
                
                logger.warn("未找到事件映射: brand={}, code={}, alarmType={}", brand, code, alarmType);
                return null;
            }
        } catch (NumberFormatException e) {
            // code 部分非数字（如 ALARM_V30 的 V30），尝试按 event_key 直接查找
            return resolveByEventKey(alarmType);
        } catch (Exception e) {
            logger.error("从alarmType字符串解析失败: alarmType={}", alarmType, e);
            return null;
        }
    }

    /**
     * 按 event_key 直接查找事件定义（支持自动入库的事件和标准事件）
     */
    public ResolveResult resolveByEventKey(String eventKey) {
        if (eventKey == null || eventKey.isEmpty()) return null;
        try (Connection conn = database.getConnection()) {
            Map<String, Object> event = CanonicalEventTable.getCanonicalEvent(conn, eventKey);
            if (event != null) {
                boolean isGeneric = Boolean.TRUE.equals(event.get("isGeneric"));
                ResolveResult result = new ResolveResult(
                    (String) event.get("eventKey"),
                    (String) event.get("nameZh"),
                    (String) event.get("nameEn"),
                    (String) event.get("category"),
                    eventKey,
                    new HashMap<>()
                );
                result.getMetadata().put("isGeneric", isGeneric);
                return result;
            }
        } catch (Exception e) {
            logger.debug("按 event_key 查找事件失败: eventKey={}, {}", eventKey, e.getMessage());
        }
        return null;
    }

    public Database getDatabase() {
        return database;
    }
}
