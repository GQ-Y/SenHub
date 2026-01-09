package com.digital.video.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.digital.video.gateway.database.AlarmRecord;
import com.digital.video.gateway.service.AlarmRecordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 报警记录控制器
 */
public class AlarmRecordController {
    private static final Logger logger = LoggerFactory.getLogger(AlarmRecordController.class);
    private final AlarmRecordService alarmRecordService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AlarmRecordController(AlarmRecordService alarmRecordService) {
        this.alarmRecordService = alarmRecordService;
    }

    /**
     * 获取报警记录列表
     * GET /api/alarm-records
     */
    public String getAlarmRecords(Request request, Response response) {
        try {
            String deviceId = request.queryParams("deviceId");
            String assemblyId = request.queryParams("assemblyId");
            String alarmType = request.queryParams("alarmType");
            String startTime = request.queryParams("startTime");
            String endTime = request.queryParams("endTime");
            String limitStr = request.queryParams("limit");
            Integer limit = limitStr != null ? Integer.parseInt(limitStr) : null;
            
            List<AlarmRecord> records = alarmRecordService.getAlarmRecords(deviceId, assemblyId, alarmType, startTime, endTime, limit);
            List<Map<String, Object>> result = records.stream()
                .map(AlarmRecord::toMap)
                .collect(java.util.stream.Collectors.toList());
            
            response.status(200);
            return createSuccessResponse(result);
        } catch (Exception e) {
            logger.error("获取报警记录列表失败", e);
            response.status(500);
            return createErrorResponse(500, "获取报警记录列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取报警记录详情
     * GET /api/alarm-records/:id
     */
    public String getAlarmRecord(Request request, Response response) {
        try {
            String recordId = request.params(":id");
            AlarmRecord record = alarmRecordService.getAlarmRecord(recordId);
            if (record == null) {
                response.status(404);
                return createErrorResponse(404, "报警记录不存在");
            }
            
            response.status(200);
            return createSuccessResponse(record.toMap());
        } catch (Exception e) {
            logger.error("获取报警记录详情失败", e);
            response.status(500);
            return createErrorResponse(500, "获取报警记录详情失败: " + e.getMessage());
        }
    }

    private String createSuccessResponse(Object data) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "success");
            response.put("data", data);
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            logger.error("创建响应失败", e);
            return "{\"code\":500,\"message\":\"Internal error\",\"data\":null}";
        }
    }

    private String createErrorResponse(int code, String message) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("code", code);
            response.put("message", message);
            response.put("data", null);
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            logger.error("创建错误响应失败", e);
            return "{\"code\":" + code + ",\"message\":\"" + message + "\",\"data\":null}";
        }
    }
}
