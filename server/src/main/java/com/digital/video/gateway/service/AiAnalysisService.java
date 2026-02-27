package com.digital.video.gateway.service;

import com.digital.video.gateway.database.AiAnalysisRecordTable;
import com.digital.video.gateway.database.Database;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * AI 分析记录服务：在工作流执行 AI 核验/警示语/TTS 时创建并更新记录。
 */
public class AiAnalysisService {
    private static final Logger logger = LoggerFactory.getLogger(AiAnalysisService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Database database;

    public AiAnalysisService(Database database) {
        this.database = database;
    }

    /**
     * 创建一条 AI 分析记录（在 ai_verify 节点执行后调用）。
     * 返回记录 id，供后续节点追加 alertText / voiceUrl。
     */
    public String createRecord(String imageUrl, String eventTitle, String eventName,
                               boolean match, String verifyReason) {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String verifyResult = match ? "pass" : "fail";
        String time = LocalDateTime.now().format(FMT);

        Map<String, Object> record = new HashMap<>();
        record.put("id", id);
        record.put("imageUrl", imageUrl);
        record.put("eventTitle", eventTitle);
        record.put("eventName", eventName);
        record.put("time", time);
        record.put("verifyResult", verifyResult);
        record.put("verifyReason", verifyReason);

        try {
            Connection conn = database.getConnection();
            synchronized (conn) {
                AiAnalysisRecordTable.insert(conn, record);
            }
            logger.info("AI分析记录已创建: id={}, event={}, result={}", id, eventName, verifyResult);
        } catch (Exception e) {
            logger.error("创建AI分析记录失败", e);
        }
        return id;
    }

    /**
     * 更新已有记录的某个字段（alertText / voiceUrl 等）。
     */
    public void updateField(String recordId, String field, String value) {
        if (recordId == null || field == null) return;
        try {
            Connection conn = database.getConnection();
            synchronized (conn) {
                AiAnalysisRecordTable.updateField(conn, recordId, field, value);
            }
        } catch (Exception e) {
            logger.error("更新AI分析记录失败: id={}, field={}", recordId, field, e);
        }
    }

    /**
     * 查询记录列表（分页 + 事件类型 + 时间范围筛选）。
     * @param eventType 事件类型，如 LOITERING、PERIMETER_INTRUSION；null 表示不过滤
     * @param startTime 开始时间（含）；null 表示不限制
     * @param endTime   结束时间（含）；null 表示不限制
     * @return [list, totalCount]
     */
    public List<Map<String, Object>> getRecords(int limit, int offset, String eventType, String startTime, String endTime) {
        try {
            Connection conn = database.getConnection();
            synchronized (conn) {
                return AiAnalysisRecordTable.list(conn, limit, offset, eventType, startTime, endTime);
            }
        } catch (Exception e) {
            logger.error("查询AI分析记录失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 统计符合条件的记录总数
     */
    public int countRecords(String eventType, String startTime, String endTime) {
        try {
            Connection conn = database.getConnection();
            synchronized (conn) {
                return AiAnalysisRecordTable.count(conn, eventType, startTime, endTime);
            }
        } catch (Exception e) {
            logger.error("统计AI分析记录失败", e);
            return 0;
        }
    }

    /**
     * 删除单条记录
     */
    public boolean deleteRecord(String id) {
        if (id == null || id.isEmpty()) return false;
        try {
            Connection conn = database.getConnection();
            synchronized (conn) {
                return AiAnalysisRecordTable.delete(conn, id);
            }
        } catch (Exception e) {
            logger.error("删除AI分析记录失败: id={}", id, e);
            return false;
        }
    }

    /**
     * 批量删除，返回实际删除条数
     */
    public int deleteRecords(List<String> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        try {
            Connection conn = database.getConnection();
            synchronized (conn) {
                return AiAnalysisRecordTable.deleteByIds(conn, ids);
            }
        } catch (Exception e) {
            logger.error("批量删除AI分析记录失败", e);
            return 0;
        }
    }
}
