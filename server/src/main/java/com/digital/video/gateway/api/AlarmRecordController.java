package com.digital.video.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.digital.video.gateway.database.AlarmRecord;
import com.digital.video.gateway.database.Assembly;
import com.digital.video.gateway.database.DeviceInfo;
import com.digital.video.gateway.device.DeviceManager;
import com.digital.video.gateway.service.AlarmRecordService;
import com.digital.video.gateway.service.AssemblyService;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 报警记录控制器
 * 返回报警记录时关联摄像头（设备）信息、装置信息、位置信息，便于前端展示“谁触发的报警、在哪”。
 */
public class AlarmRecordController {
    private static final Logger logger = LoggerFactory.getLogger(AlarmRecordController.class);
    private final AlarmRecordService alarmRecordService;
    private final DeviceManager deviceManager;
    private final AssemblyService assemblyService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AlarmRecordController(AlarmRecordService alarmRecordService) {
        this.alarmRecordService = alarmRecordService;
        this.deviceManager = null;
        this.assemblyService = null;
    }

    public AlarmRecordController(AlarmRecordService alarmRecordService, DeviceManager deviceManager, AssemblyService assemblyService) {
        this.alarmRecordService = alarmRecordService;
        this.deviceManager = deviceManager;
        this.assemblyService = assemblyService;
    }

    /**
     * 为单条报警记录附加设备名、装置名、位置信息
     */
    private Map<String, Object> enrichRecord(AlarmRecord record) {
        Map<String, Object> map = record.toMap();
        if (deviceManager != null && record.getDeviceId() != null) {
            DeviceInfo device = deviceManager.getDevice(record.getDeviceId());
            if (device != null) {
                map.put("deviceName", device.getName());
                map.put("deviceIp", device.getIp());
                map.put("deviceBrand", device.getBrand());
            }
        }
        if (assemblyService != null && record.getAssemblyId() != null) {
            Assembly assembly = assemblyService.getAssembly(record.getAssemblyId());
            if (assembly != null) {
                map.put("assemblyName", assembly.getName());
                Map<String, Object> position = new HashMap<>();
                position.put("location", assembly.getLocation());
                if (assembly.getLongitude() != null) position.put("longitude", assembly.getLongitude());
                if (assembly.getLatitude() != null) position.put("latitude", assembly.getLatitude());
                map.put("position", position);
            }
        }
        return map;
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
            List<Map<String, Object>> result = records.stream().map(this::enrichRecord).collect(Collectors.toList());

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
            ctx.result(createSuccessResponse(enrichRecord(record)));
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
