package com.digital.video.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.digital.video.gateway.database.Database;
import com.digital.video.gateway.database.RadarDevice;
import com.digital.video.gateway.database.RadarDeviceDAO;
import com.digital.video.gateway.database.RadarBackground;
import com.digital.video.gateway.database.RadarIntrusionRecord;
import com.digital.video.gateway.driver.livox.model.DefenseZone;
import com.digital.video.gateway.service.*;
import com.digital.video.gateway.service.RadarTestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.sql.Connection;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 雷达控制API接口
 */
public class RadarController {
    private static final Logger logger = LoggerFactory.getLogger(RadarController.class);
    
    private final RadarTestService radarTestService;
    @SuppressWarnings("unused")
    private final Database database;
    private final Connection connection;
    private final RadarDeviceDAO radarDeviceDAO;
    private final BackgroundModelService backgroundService;
    private final DefenseZoneService defenseZoneService;
    private final IntrusionDetectionService intrusionDetectionService;
    private final RadarService radarService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RadarController(RadarTestService radarTestService, Database database, 
                          BackgroundModelService backgroundService,
                          DefenseZoneService defenseZoneService,
                          IntrusionDetectionService intrusionDetectionService,
                          RadarService radarService) {
        this.radarTestService = radarTestService;
        this.database = database;
        this.connection = database.getConnection();
        this.radarDeviceDAO = new RadarDeviceDAO(connection);
        this.backgroundService = backgroundService;
        this.defenseZoneService = defenseZoneService;
        this.intrusionDetectionService = intrusionDetectionService;
        this.radarService = radarService;
    }

    /**
     * 测试雷达连接
     * POST /api/radar/test
     */
    public Object testConnection(Request request, Response response) {
        try {
            String ip = request.queryParams("ip") != null ? request.queryParams("ip") : "192.168.1.115";
            String resultText = radarTestService.testConnection(ip);
            return createSuccessResponse(resultText);
        } catch (Exception e) {
            logger.error("测试雷达连接失败", e);
            response.status(500);
            return createErrorResponse(500, e.getMessage());
        }
    }

    // ==================== 雷达设备管理 ====================

    /**
     * 获取雷达设备列表
     * GET /api/radar/devices
     */
    public Object getRadarDevices(Request request, Response response) {
        try {
            List<RadarDevice> devices = radarDeviceDAO.getAll();
            List<Map<String, Object>> deviceList = devices.stream()
                    .map(RadarDevice::toMap)
                    .collect(Collectors.toList());
            return createSuccessResponse(deviceList);
        } catch (Exception e) {
            logger.error("获取雷达设备列表失败", e);
            response.status(500);
            return createErrorResponse(500, e.getMessage());
        }
    }

    /**
     * 添加雷达设备
     * POST /api/radar/devices
     */
    public Object addRadarDevice(Request request, Response response) {
        try {
            Map<String, Object> body = objectMapper.readValue(request.body(), Map.class);
            RadarDevice device = new RadarDevice();
            device.setDeviceId((String) body.get("deviceId"));
            device.setRadarIp((String) body.get("radarIp"));
            device.setRadarName((String) body.get("radarName"));
            device.setAssemblyId((String) body.get("assemblyId"));
            device.setStatus(0);
            
            if (radarDeviceDAO.saveOrUpdate(device)) {
                return createSuccessResponse(device.toMap());
            } else {
                response.status(400);
                return createErrorResponse(400, "添加雷达设备失败");
            }
        } catch (Exception e) {
            logger.error("添加雷达设备失败", e);
            response.status(500);
            return createErrorResponse(500, e.getMessage());
        }
    }

    // ==================== 背景建模 ====================

    /**
     * 开始采集背景
     * POST /api/radar/:deviceId/background/start
     */
    public Object startBackgroundCollection(Request request, Response response) {
        try {
            String deviceId = request.params("deviceId");
            Map<String, Object> body = objectMapper.readValue(request.body(), Map.class);
            int durationSeconds = body.get("durationSeconds") != null ? 
                    ((Number) body.get("durationSeconds")).intValue() : 10;
            float gridResolution = body.get("gridResolution") != null ? 
                    ((Number) body.get("gridResolution")).floatValue() : 0.05f;
            
            String backgroundId = radarService.startBackgroundCollection(deviceId, durationSeconds, gridResolution);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> result = new HashMap<>();
            result.put("backgroundId", backgroundId);
            result.put("status", "collecting");
            result.put("estimatedTime", durationSeconds);
            
            return createSuccessResponse(result);
        } catch (Exception e) {
            logger.error("开始采集背景失败", e);
            response.status(500);
            return createErrorResponse(500, e.getMessage());
        }
    }

    /**
     * 停止采集背景
     * POST /api/radar/:deviceId/background/stop
     */
    public Object stopBackgroundCollection(Request request, Response response) {
        try {
            String deviceId = request.params("deviceId");
            String backgroundId = radarService.stopBackgroundCollection(deviceId);
            
            if (backgroundId != null) {
                RadarBackground background = backgroundService.getBackgroundDAO().getByBackgroundId(backgroundId);
                Map<String, Object> result = new HashMap<>();
                result.put("backgroundId", backgroundId);
                result.put("status", "ready");
                result.put("frameCount", background != null ? background.getFrameCount() : 0);
                result.put("pointCount", background != null ? background.getPointCount() : 0);
                return createSuccessResponse(result);
            } else {
                response.status(400);
                return createErrorResponse(400, "停止采集失败");
            }
        } catch (Exception e) {
            logger.error("停止采集背景失败", e);
            response.status(500);
            return createErrorResponse(500, e.getMessage());
        }
    }

    /**
     * 获取采集状态
     * GET /api/radar/:deviceId/background/status
     */
    public Object getBackgroundStatus(Request request, Response response) {
        try {
            String deviceId = request.params("deviceId");
            BackgroundModelService.CollectionStatus status = backgroundService.getCollectionStatus(deviceId);
            if (status != null) {
                return createSuccessResponse(status);
            } else {
                response.status(404);
                return createErrorResponse(404, "未找到采集状态");
            }
        } catch (Exception e) {
            logger.error("获取采集状态失败", e);
            response.status(500);
            return createErrorResponse(500, e.getMessage());
        }
    }

    // ==================== 防区管理 ====================

    /**
     * 获取防区列表
     * GET /api/radar/:deviceId/zones
     */
    public Object getZones(Request request, Response response) {
        try {
            String deviceId = request.params("deviceId");
            List<DefenseZone> zones = defenseZoneService.getZonesByDeviceId(deviceId);
            List<Map<String, Object>> zoneList = zones.stream()
                    .map(this::convertZoneToMap)
                    .collect(Collectors.toList());
            return createSuccessResponse(zoneList);
        } catch (Exception e) {
            logger.error("获取防区列表失败", e);
            response.status(500);
            return createErrorResponse(500, e.getMessage());
        }
    }

    /**
     * 创建防区
     * POST /api/radar/:deviceId/zones
     */
    public Object createZone(Request request, Response response) {
        try {
            String deviceId = request.params("deviceId");
            Map<String, Object> body = objectMapper.readValue(request.body(), Map.class);
            
            DefenseZone zone = new DefenseZone();
            zone.setDeviceId(deviceId);
            zone.setBackgroundId((String) body.get("backgroundId"));
            zone.setZoneType((String) body.get("zoneType"));
            zone.setName((String) body.get("name"));
            zone.setDescription((String) body.get("description"));
            zone.setEnabled(true);
            
            if (body.get("shrinkDistanceCm") != null) {
                zone.setShrinkDistanceCm(((Number) body.get("shrinkDistanceCm")).intValue());
            }
            if (body.get("minX") != null) {
                zone.setMinX(((Number) body.get("minX")).floatValue());
                zone.setMaxX(((Number) body.get("maxX")).floatValue());
                zone.setMinY(((Number) body.get("minY")).floatValue());
                zone.setMaxY(((Number) body.get("maxY")).floatValue());
                zone.setMinZ(((Number) body.get("minZ")).floatValue());
                zone.setMaxZ(((Number) body.get("maxZ")).floatValue());
            }
            if (body.get("cameraDeviceId") != null) {
                zone.setCameraDeviceId((String) body.get("cameraDeviceId"));
                zone.setCameraChannel(body.get("cameraChannel") != null ? 
                        ((Number) body.get("cameraChannel")).intValue() : 1);
            }
            
            String zoneId = defenseZoneService.createZone(zone);
            if (zoneId != null) {
                return createSuccessResponse(convertZoneToMap(defenseZoneService.getZone(zoneId)));
            } else {
                response.status(400);
                return createErrorResponse(400, "创建防区失败");
            }
        } catch (Exception e) {
            logger.error("创建防区失败", e);
            response.status(500);
            return createErrorResponse(500, e.getMessage());
        }
    }

    // ==================== 侵入记录 ====================

    /**
     * 获取侵入记录列表
     * GET /api/radar/:deviceId/intrusions
     */
    public Object getIntrusions(Request request, Response response) {
        try {
            String deviceId = request.params("deviceId");
            String zoneId = request.queryParams("zoneId");
            String startTimeStr = request.queryParams("startTime");
            String endTimeStr = request.queryParams("endTime");
            int page = request.queryParams("page") != null ? Integer.parseInt(request.queryParams("page")) : 1;
            int pageSize = request.queryParams("pageSize") != null ? Integer.parseInt(request.queryParams("pageSize")) : 20;
            
            Date startTime = startTimeStr != null ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(startTimeStr) : null;
            Date endTime = endTimeStr != null ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(endTimeStr) : null;
            
            List<RadarIntrusionRecord> records = intrusionDetectionService.getIntrusionRecords(
                    deviceId, zoneId, startTime, endTime, page, pageSize);
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> recordList = records.stream()
                    .map(RadarIntrusionRecord::toMap)
                    .collect(Collectors.toList());
            
            @SuppressWarnings("unchecked")
            Map<String, Object> result = new HashMap<>();
            result.put("records", recordList);
            result.put("page", page);
            result.put("pageSize", pageSize);
            
            return createSuccessResponse(result);
        } catch (Exception e) {
            logger.error("获取侵入记录失败", e);
            response.status(500);
            return createErrorResponse(500, e.getMessage());
        }
    }

    // ==================== 辅助方法 ====================

    private Map<String, Object> convertZoneToMap(DefenseZone zone) {
        Map<String, Object> map = new HashMap<>();
        map.put("zoneId", zone.getZoneId());
        map.put("deviceId", zone.getDeviceId());
        map.put("backgroundId", zone.getBackgroundId());
        map.put("zoneType", zone.getZoneType());
        map.put("shrinkDistanceCm", zone.getShrinkDistanceCm());
        map.put("minX", zone.getMinX());
        map.put("maxX", zone.getMaxX());
        map.put("minY", zone.getMinY());
        map.put("maxY", zone.getMaxY());
        map.put("minZ", zone.getMinZ());
        map.put("maxZ", zone.getMaxZ());
        map.put("cameraDeviceId", zone.getCameraDeviceId());
        map.put("cameraChannel", zone.getCameraChannel());
        map.put("enabled", zone.getEnabled());
        map.put("name", zone.getName());
        map.put("description", zone.getDescription());
        return map;
    }

    private String createSuccessResponse(Object data) {
        try {
            Map<String, Object> result = new HashMap<>();
            result.put("code", 200);
            result.put("message", "success");
            result.put("data", data);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            logger.error("创建响应失败", e);
            return "{\"code\":500,\"message\":\"创建响应失败\"}";
        }
    }

    private String createErrorResponse(int code, String message) {
        try {
            Map<String, Object> result = new HashMap<>();
            result.put("code", code);
            result.put("message", message);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return "{\"code\":" + code + ",\"message\":\"" + message + "\"}";
        }
    }
}
