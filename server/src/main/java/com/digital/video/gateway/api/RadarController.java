package com.digital.video.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.digital.video.gateway.database.Database;
import com.digital.video.gateway.database.RadarDevice;
import com.digital.video.gateway.database.RadarDeviceDAO;
import com.digital.video.gateway.database.RadarBackground;
import com.digital.video.gateway.database.RadarIntrusionRecord;
import com.digital.video.gateway.database.RadarIntrusionRecordDAO;
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
import java.util.UUID;

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
    private final RadarIntrusionRecordDAO intrusionRecordDAO;
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
        this.intrusionRecordDAO = new RadarIntrusionRecordDAO(connection);
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
            if (radarTestService == null) {
                response.status(503);
                return createErrorResponse(503, "雷达测试服务未启动，可能是Livox SDK依赖问题");
            }

            String ip = request.queryParams("ip") != null ? request.queryParams("ip") : "192.168.1.115";
            RadarTestService.RadarDetectionResult result = radarTestService.testConnection(ip);

            Map<String, Object> payload = new HashMap<>();
            payload.put("reachable", result.isReachable());
            payload.put("message", result.getMessage());
            payload.put("ip", result.getIp());
            if (result.getRadarSerial() != null) {
                payload.put("radarSerial", result.getRadarSerial());
            }
            return createSuccessResponse(payload);
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
     * 
     * 支持两种模式：
     * 1. 有 SN：按原逻辑，SN 作为设备唯一标识
     * 2. 无 SN：使用 IP 作为临时标识，雷达上线后自动填充 SN
     */
    public Object addRadarDevice(Request request, Response response) {
        try {
            Map<String, Object> body = objectMapper.readValue(request.body(), new TypeReference<Map<String, Object>>() {});

            // 验证必填字段
            String radarIp = (String) body.get("radarIp");
            String radarName = (String) body.get("radarName");
            String radarSerial = (String) body.get("radarSerial");

            if (radarIp == null || radarIp.trim().isEmpty()) {
                response.status(400);
                return createErrorResponse(400, "雷达IP地址不能为空");
            }

            if (radarName == null || radarName.trim().isEmpty()) {
                response.status(400);
                return createErrorResponse(400, "雷达名称不能为空");
            }

            // 验证IP格式
            if (!isValidIpAddress(radarIp)) {
                response.status(400);
                return createErrorResponse(400, "无效的IP地址格式");
            }

            radarIp = radarIp.trim();
            radarSerial = (radarSerial != null && !radarSerial.trim().isEmpty()) ? radarSerial.trim() : null;

            // 检查IP是否已存在
            List<RadarDevice> existingDevices = radarDeviceDAO.getAll();
            RadarDevice existingByIp = null;
            RadarDevice existingBySerial = null;
            
            for (RadarDevice existing : existingDevices) {
                if (radarIp.equals(existing.getRadarIp())) {
                    existingByIp = existing;
                }
                if (radarSerial != null && radarSerial.equals(existing.getRadarSerial())) {
                    existingBySerial = existing;
                }
            }

            // 如果 IP 已存在且不是同一设备（通过 SN 判断），报错
            if (existingByIp != null && existingBySerial != null && 
                    !existingByIp.getDeviceId().equals(existingBySerial.getDeviceId())) {
                response.status(400);
                return createErrorResponse(400, "该IP地址已被其他设备使用: " + radarIp);
            }

            // 确定设备ID
            String deviceId;
            boolean isUpdate = false;
            
            if (radarSerial != null) {
                // 有 SN：使用 SN 作为设备ID
                deviceId = radarSerial;
                if (existingBySerial != null) {
                    isUpdate = true;
                }
            } else {
                // 无 SN：检查是否有同 IP 设备
                if (existingByIp != null) {
                    // 更新已有设备
                    deviceId = existingByIp.getDeviceId();
                    isUpdate = true;
                } else {
                    // 创建新设备，使用 IP 作为临时标识
                    deviceId = "radar_" + radarIp.replace(".", "_");
                }
            }

            RadarDevice device = new RadarDevice();
            device.setDeviceId(deviceId);
            device.setRadarIp(radarIp);
            device.setRadarName(radarName.trim());
            device.setAssemblyId((String) body.get("assemblyId")); // 可选
            device.setRadarSerial(radarSerial); // 可能为 null

            if (isUpdate) {
                RadarDevice existing = existingBySerial != null ? existingBySerial : existingByIp;
                device.setStatus(existing.getStatus());
                device.setCurrentBackgroundId(existing.getCurrentBackgroundId());
            } else {
                device.setStatus(0);
            }

            if (radarDeviceDAO.saveOrUpdate(device)) {
                String snStatus = radarSerial != null ? "SN=" + radarSerial : "SN待自动填充";
                logger.info("雷达设备已{}: deviceId={}, radarIp={}, radarName={}, {}",
                        isUpdate ? "更新" : "添加",
                        deviceId, radarIp, radarName, snStatus);
                
                Map<String, Object> result = device.toMap();
                if (radarSerial == null) {
                    result.put("snPending", true);
                    result.put("message", "设备已添加，SN将在雷达上线后自动填充");
                }
                return createSuccessResponse(result);
            } else {
                response.status(400);
                return createErrorResponse(400, isUpdate ? "更新雷达设备失败" : "添加雷达设备失败");
            }
        } catch (Exception e) {
            logger.error("添加雷达设备失败", e);
            response.status(500);
            return createErrorResponse(500, e.getMessage());
        }
    }

    /**
     * 更新雷达设备
     * PUT /api/radar/devices/:deviceId
     */
    public Object updateRadarDevice(Request request, Response response) {
        try {
            String deviceId = request.params("deviceId");
            Map<String, Object> body = objectMapper.readValue(request.body(), new TypeReference<Map<String, Object>>() {});
            
            // 查找现有设备
            RadarDevice existingDevice = radarDeviceDAO.getByDeviceId(deviceId);
            if (existingDevice == null) {
                response.status(404);
                return createErrorResponse(404, "雷达设备不存在: " + deviceId);
            }
            
            // 更新字段
            String radarIp = (String) body.get("radarIp");
            String radarName = (String) body.get("radarName");
            String assemblyId = (String) body.get("assemblyId");
            
            if (radarIp != null && !radarIp.trim().isEmpty()) {
                if (!isValidIpAddress(radarIp)) {
                    response.status(400);
                    return createErrorResponse(400, "无效的IP地址格式");
                }
                // 检查IP是否被其他设备占用
                List<RadarDevice> allDevices = radarDeviceDAO.getAll();
                for (RadarDevice d : allDevices) {
                    if (radarIp.equals(d.getRadarIp()) && !deviceId.equals(d.getDeviceId())) {
                        response.status(400);
                        return createErrorResponse(400, "该IP地址已被其他设备使用: " + radarIp);
                    }
                }
                existingDevice.setRadarIp(radarIp.trim());
            }
            
            if (radarName != null && !radarName.trim().isEmpty()) {
                existingDevice.setRadarName(radarName.trim());
            }
            
            if (body.containsKey("assemblyId")) {
                existingDevice.setAssemblyId(assemblyId);
            }
            
            if (radarDeviceDAO.saveOrUpdate(existingDevice)) {
                logger.info("雷达设备已更新: deviceId={}", deviceId);
                return createSuccessResponse(existingDevice.toMap());
            } else {
                response.status(400);
                return createErrorResponse(400, "更新雷达设备失败");
            }
        } catch (Exception e) {
            logger.error("更新雷达设备失败", e);
            response.status(500);
            return createErrorResponse(500, e.getMessage());
        }
    }

    /**
     * 删除雷达设备
     * DELETE /api/radar/devices/:deviceId
     * 级联删除所有关联数据：背景模型、防区、侵入记录
     */
    public Object deleteRadarDevice(Request request, Response response) {
        try {
            String deviceId = request.params("deviceId");
            
            // 查找现有设备
            RadarDevice existingDevice = radarDeviceDAO.getByDeviceId(deviceId);
            if (existingDevice == null) {
                response.status(404);
                return createErrorResponse(404, "雷达设备不存在: " + deviceId);
            }
            
            logger.info("开始删除雷达设备及关联数据: deviceId={}", deviceId);
            
            // 1. 删除侵入记录
            int deletedIntrusions = intrusionDetectionService.clearIntrusionRecords(deviceId);
            logger.info("已删除侵入记录: deviceId={}, 数量={}", deviceId, deletedIntrusions);
            
            // 2. 删除防区
            List<DefenseZone> zones = defenseZoneService.getZonesByDeviceId(deviceId);
            int deletedZones = 0;
            for (DefenseZone zone : zones) {
                if (defenseZoneService.deleteZone(zone.getZoneId())) {
                    deletedZones++;
                }
            }
            logger.info("已删除防区: deviceId={}, 数量={}", deviceId, deletedZones);
            
            // 3. 删除背景模型
            List<RadarBackground> backgrounds = backgroundService.getBackgroundDAO().getByDeviceId(deviceId);
            int deletedBackgrounds = 0;
            for (RadarBackground bg : backgrounds) {
                if (backgroundService.deleteBackground(bg.getBackgroundId())) {
                    deletedBackgrounds++;
                }
            }
            logger.info("已删除背景模型: deviceId={}, 数量={}", deviceId, deletedBackgrounds);
            
            // 4. 删除雷达设备
            if (radarDeviceDAO.delete(deviceId)) {
                logger.info("雷达设备已删除: deviceId={}", deviceId);
                
                Map<String, Object> result = new HashMap<>();
                result.put("deviceId", deviceId);
                result.put("deletedIntrusions", deletedIntrusions);
                result.put("deletedZones", deletedZones);
                result.put("deletedBackgrounds", deletedBackgrounds);
                
                return createSuccessResponse(result);
            } else {
                response.status(400);
                return createErrorResponse(400, "删除雷达设备失败");
            }
        } catch (Exception e) {
            logger.error("删除雷达设备失败", e);
            response.status(500);
            return createErrorResponse(500, e.getMessage());
        }
    }

    /**
     * 验证IP地址格式
     */
    private boolean isValidIpAddress(String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            return false;
        }
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        try {
            for (String part : parts) {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ==================== 背景建模 ====================

    /**
     * 开始采集背景
     * POST /api/radar/:deviceId/background/start
     */
    public Object startBackgroundCollection(Request request, Response response) {
        try {
            if (radarService == null) {
                response.status(503);
                return createErrorResponse(503, "雷达服务未启动，请先添加雷达设备");
            }

            String deviceId = request.params("deviceId");
            Map<String, Object> body = objectMapper.readValue(request.body(), new TypeReference<Map<String, Object>>() {});
            int durationSeconds = body.get("durationSeconds") != null
                    ? ((Number) body.get("durationSeconds")).intValue()
                    : 10;
            float gridResolution = body.get("gridResolution") != null
                    ? ((Number) body.get("gridResolution")).floatValue()
                    : 0.05f;

            String backgroundId = radarService.startBackgroundCollection(deviceId, durationSeconds, gridResolution);

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
            if (radarService == null) {
                response.status(503);
                return createErrorResponse(503, "雷达服务未启动，请先添加雷达设备");
            }

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

    /**
     * 获取采集中的点云数据（用于实时预览）
     * GET /api/radar/:deviceId/background/collecting/points
     * 注意：实时点云数据应通过WebSocket获取，此接口仅用于采集过程中的预览
     */
    public Object getCollectingPointCloud(Request request, Response response) {
        try {
            String deviceId = request.params("deviceId");
            int maxPoints = request.queryParams("maxPoints") != null
                    ? Integer.parseInt(request.queryParams("maxPoints"))
                    : 5000;

            List<com.digital.video.gateway.driver.livox.model.Point> points = backgroundService
                    .getCollectingPointCloud(deviceId, maxPoints);

            // 转换为Map格式
            List<Map<String, Object>> pointList = new ArrayList<>();
            for (com.digital.video.gateway.driver.livox.model.Point point : points) {
                Map<String, Object> p = new HashMap<>();
                p.put("x", point.x);
                p.put("y", point.y);
                p.put("z", point.z);
                if (point.reflectivity != 0) {
                    p.put("r", point.reflectivity);
                }
                pointList.add(p);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("points", pointList);
            result.put("pointCount", pointList.size());

            return createSuccessResponse(result);
        } catch (Exception e) {
            logger.error("获取采集点云数据失败", e);
            response.status(500);
            return createErrorResponse(500, e.getMessage());
        }
    }

    /**
     * 获取背景点云数据（从文件读取）
     * GET /api/radar/:deviceId/background/:backgroundId/points
     */
    public Object getBackgroundPoints(Request request, Response response) {
        try {
            String backgroundId = request.params("backgroundId");
            int maxPoints = request.queryParams("maxPoints") != null
                    ? Integer.parseInt(request.queryParams("maxPoints"))
                    : 10000;

            // 从文件加载背景点云
            List<com.digital.video.gateway.driver.livox.model.Point> points = backgroundService
                    .loadBackgroundPointsFromFile(backgroundId, maxPoints);

            // 转换为Map格式
            List<Map<String, Object>> pointList = new ArrayList<>();
            for (com.digital.video.gateway.driver.livox.model.Point point : points) {
                Map<String, Object> p = new HashMap<>();
                p.put("x", point.x);
                p.put("y", point.y);
                p.put("z", point.z);
                if (point.reflectivity != 0) {
                    p.put("r", point.reflectivity);
                }
                pointList.add(p);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("points", pointList);
            result.put("pointCount", pointList.size());
            result.put("backgroundId", backgroundId);

            return createSuccessResponse(result);
        } catch (Exception e) {
            logger.error("获取背景点云数据失败", e);
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
            Map<String, Object> body = objectMapper.readValue(request.body(), new TypeReference<Map<String, Object>>() {});

            DefenseZone zone = new DefenseZone();
            zone.setDeviceId(deviceId);
            zone.setBackgroundId((String) body.get("backgroundId"));
            zone.setZoneType((String) body.get("zoneType"));
            zone.setName((String) body.get("name"));
            zone.setDescription((String) body.get("description"));
            zone.setEnabled(body.get("enabled") != null ? ((Boolean) body.get("enabled")) : true);

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
                zone.setCameraChannel(
                        body.get("cameraChannel") != null ? ((Number) body.get("cameraChannel")).intValue() : 1);
            }
            if (body.get("coordinateTransform") != null) {
                zone.setCoordinateTransformJson(coordinateTransformToJsonString(body.get("coordinateTransform")));
            }

            String zoneId = defenseZoneService.createZone(zone);
            if (zoneId != null) {
                // 重新加载检测上下文
                radarService.reloadDeviceDetectionContext(deviceId);
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

    /**
     * 更新防区
     * PUT /api/radar/:deviceId/zones/:zoneId
     */
    public Object updateZone(Request request, Response response) {
        try {
            String deviceId = request.params("deviceId");
            String zoneId = request.params("zoneId");
            Map<String, Object> body = objectMapper.readValue(request.body(), new TypeReference<Map<String, Object>>() {});

            DefenseZone zone = new DefenseZone();
            zone.setZoneId(zoneId);
            zone.setDeviceId(deviceId);
            zone.setBackgroundId((String) body.get("backgroundId"));
            zone.setZoneType((String) body.get("zoneType"));
            zone.setName((String) body.get("name"));
            zone.setDescription((String) body.get("description"));
            if (body.get("enabled") != null) {
                zone.setEnabled(((Boolean) body.get("enabled")));
            }

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
                zone.setCameraChannel(
                        body.get("cameraChannel") != null ? ((Number) body.get("cameraChannel")).intValue() : 1);
            }
            if (body.get("coordinateTransform") != null) {
                zone.setCoordinateTransformJson(coordinateTransformToJsonString(body.get("coordinateTransform")));
            } else {
                DefenseZone existing = defenseZoneService.getZone(zoneId);
                if (existing != null && existing.getCoordinateTransformJson() != null) {
                    zone.setCoordinateTransformJson(existing.getCoordinateTransformJson());
                }
            }

            if (defenseZoneService.updateZone(zoneId, zone)) {
                // 重新加载检测上下文
                radarService.reloadDeviceDetectionContext(deviceId);
                return createSuccessResponse(convertZoneToMap(defenseZoneService.getZone(zoneId)));
            } else {
                response.status(400);
                return createErrorResponse(400, "更新防区失败");
            }
        } catch (Exception e) {
            logger.error("更新防区失败", e);
            response.status(500);
            return createErrorResponse(500, e.getMessage());
        }
    }

    /**
     * 删除防区
     * DELETE /api/radar/:deviceId/zones/:zoneId
     */
    public Object deleteZone(Request request, Response response) {
        try {
            String deviceId = request.params("deviceId");
            String zoneId = request.params("zoneId");
            if (defenseZoneService.deleteZone(zoneId)) {
                // 重新加载检测上下文
                radarService.reloadDeviceDetectionContext(deviceId);
                return createSuccessResponse(null);
            } else {
                response.status(400);
                return createErrorResponse(400, "删除防区失败");
            }
        } catch (Exception e) {
            logger.error("删除防区失败", e);
            response.status(500);
            return createErrorResponse(500, e.getMessage());
        }
    }

    /**
     * 切换防区启用状态
     * PUT /api/radar/:deviceId/zones/:zoneId/toggle
     */
    public Object toggleZone(Request request, Response response) {
        try {
            String zoneId = request.params("zoneId");
            DefenseZone zone = defenseZoneService.getZone(zoneId);
            if (zone == null) {
                response.status(404);
                return createErrorResponse(404, "防区不存在");
            }

            zone.setEnabled(!zone.getEnabled());
            if (defenseZoneService.updateZone(zoneId, zone)) {
                // 重新加载检测上下文
                String deviceId = request.params("deviceId");
                radarService.reloadDeviceDetectionContext(deviceId);
                return createSuccessResponse(convertZoneToMap(defenseZoneService.getZone(zoneId)));
            } else {
                response.status(400);
                return createErrorResponse(400, "切换防区状态失败");
            }
        } catch (Exception e) {
            logger.error("切换防区状态失败", e);
            response.status(500);
            return createErrorResponse(500, e.getMessage());
        }
    }

    /**
     * 获取背景模型列表
     * GET /api/radar/:deviceId/backgrounds
     */
    public Object getBackgrounds(Request request, Response response) {
        try {
            String deviceId = request.params("deviceId");
            List<RadarBackground> backgrounds = backgroundService.getBackgroundDAO().getByDeviceId(deviceId);
            List<Map<String, Object>> backgroundList = backgrounds.stream()
                    .map(bg -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("backgroundId", bg.getBackgroundId());
                        map.put("deviceId", bg.getDeviceId());
                        map.put("status", bg.getStatus());
                        map.put("durationSeconds", bg.getDurationSeconds());
                        map.put("gridResolution", bg.getGridResolution());
                        map.put("frameCount", bg.getFrameCount());
                        map.put("pointCount", bg.getPointCount());
                        map.put("createdAt", bg.getCreatedAt());
                        return map;
                    })
                    .collect(Collectors.toList());
            return createSuccessResponse(backgroundList);
        } catch (Exception e) {
            logger.error("获取背景列表失败", e);
            response.status(500);
            return createErrorResponse(500, e.getMessage());
        }
    }

    /**
     * 删除背景模型
     * DELETE /api/radar/:deviceId/backgrounds/:backgroundId
     */
    public Object deleteBackground(Request request, Response response) {
        try {
            String deviceId = request.params("deviceId");
            String backgroundId = request.params("backgroundId");

            // 删除数据库记录和文件
            boolean deleted = backgroundService.deleteBackground(backgroundId);

            if (deleted) {
                logger.info("背景模型删除成功: deviceId={}, backgroundId={}", deviceId, backgroundId);
                return createSuccessResponse(null);
            } else {
                response.status(400);
                return createErrorResponse(400, "删除背景模型失败");
            }
        } catch (Exception e) {
            logger.error("删除背景模型失败", e);
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
            int pageSize = request.queryParams("pageSize") != null ? Integer.parseInt(request.queryParams("pageSize"))
                    : 20;

            Date startTime = startTimeStr != null ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(startTimeStr)
                    : null;
            Date endTime = endTimeStr != null ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(endTimeStr) : null;

            List<RadarIntrusionRecord> records = intrusionDetectionService.getIntrusionRecords(
                    deviceId, zoneId, startTime, endTime, page, pageSize);

            List<Map<String, Object>> recordList = records.stream()
                    .map(RadarIntrusionRecord::toMap)
                    .collect(Collectors.toList());

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

    /**
     * 清空侵入记录
     * DELETE /api/radar/:deviceId/intrusions
     */
    public Object clearIntrusions(Request request, Response response) {
        try {
            String deviceId = request.params("deviceId");
            int count = intrusionDetectionService.clearIntrusionRecords(deviceId);
            Map<String, Object> result = new HashMap<>();
            result.put("deletedCount", count);
            logger.info("清空侵入记录: deviceId={}, 删除数量={}", deviceId, count);
            return createSuccessResponse(result);
        } catch (Exception e) {
            logger.error("清空侵入记录失败", e);
            response.status(500);
            return createErrorResponse(500, e.getMessage());
        }
    }

    /**
     * 获取侵入数据文件
     * GET /api/radar/intrusions/:id/data
     */
    public Object getIntrusionData(Request request, Response response) {
        try {
            String id = request.params("id");
            RadarIntrusionRecord record = intrusionRecordDAO.getById(id);
            if (record == null) {
                response.status(404);
                return createErrorResponse(404, "记录不存在");
            }

            String clusterId = record.getClusterId();
            if (clusterId == null || !clusterId.startsWith("TRAJECTORY:")) {
                response.status(404);
                return createErrorResponse(404, "该记录没有轨迹数据");
            }

            String relativePath = clusterId.substring("TRAJECTORY:".length());
            java.io.File file = new java.io.File(relativePath);
            if (!file.exists()) {
                response.status(404);
                return createErrorResponse(404, "数据文件未找到");
            }

            // 读取JSON文件并解析为Map，然后包装在标准响应格式中
            String jsonContent = new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            Map<String, Object> recordData = objectMapper.readValue(jsonContent, new TypeReference<Map<String, Object>>() {});
            return createSuccessResponse(recordData);
        } catch (Exception e) {
            logger.error("读取侵入数据文件失败", e);
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
        map.put("coordinateTransform", zone.getCoordinateTransformJson());
        map.put("enabled", zone.getEnabled());
        map.put("name", zone.getName());
        map.put("description", zone.getDescription());
        return map;
    }

    /** 将 coordinateTransform 请求参数转为 JSON 字符串（支持传对象或已序列化字符串） */
    private String coordinateTransformToJsonString(Object coordinateTransform) {
        if (coordinateTransform == null) return null;
        if (coordinateTransform instanceof String) return (String) coordinateTransform;
        try {
            return objectMapper.writeValueAsString(coordinateTransform);
        } catch (Exception e) {
            return null;
        }
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
