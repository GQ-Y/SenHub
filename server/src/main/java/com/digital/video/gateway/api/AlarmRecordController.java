package com.digital.video.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.digital.video.gateway.database.AlarmRecord;
import com.digital.video.gateway.service.AlarmRecordService;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    public void getAlarmRecords(Context ctx) {
        try {
            String deviceId = ctx.queryParam("deviceId");
            String assemblyId = ctx.queryParam("assemblyId");
            String alarmType = ctx.queryParam("alarmType");
            String startTime = ctx.queryParam("startTime");
            String endTime = ctx.queryParam("endTime");
            String limitStr = ctx.queryParam("limit");
            Integer limit = limitStr != null ? Integer.parseInt(limitStr) : null;

            List<AlarmRecord> records = alarmRecordService.getAlarmRecords(deviceId, assemblyId, alarmType, startTime, endTime, limit);
            List<Map<String, Object>> result = records.stream().map(AlarmRecord::toMap).collect(Collectors.toList());

            ctx.status(200);
            ctx.result(createSuccessResponse(result));
        } catch (Exception e) {
            logger.error("获取报警记录列表失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "获取报警记录列表失败: " + e.getMessage()));
        }
    }

    public void getAlarmRecord(Context ctx) {
        try {
            String recordId = ctx.pathParam("id");
            AlarmRecord record = alarmRecordService.getAlarmRecord(recordId);
            if (record == null) {
                ctx.status(404);
                ctx.result(createErrorResponse(404, "报警记录不存在"));
                return;
            }
            ctx.status(200);
            ctx.result(createSuccessResponse(record.toMap()));
        } catch (Exception e) {
            logger.error("获取报警记录详情失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, "获取报警记录详情失败: " + e.getMessage()));
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
