package com.digital.video.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.digital.video.gateway.database.Database;
import com.digital.video.gateway.database.DeviceInfo;
import com.digital.video.gateway.database.RadarDevice;
import com.digital.video.gateway.database.RadarDeviceDAO;
import com.digital.video.gateway.database.RadarBackground;
import com.digital.video.gateway.database.RadarIntrusionRecord;
import com.digital.video.gateway.database.RadarIntrusionRecordDAO;
import com.digital.video.gateway.driver.livox.model.DefenseZone;
import com.digital.video.gateway.service.*;
import com.digital.video.gateway.service.RadarTestService;
import com.digital.video.gateway.driver.livox.algorithm.CalibrationSolver;
import com.digital.video.gateway.driver.livox.algorithm.CoordinateTransform;
import com.digital.video.gateway.driver.livox.model.Point;
import com.digital.video.gateway.database.Assembly;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private final RadarIntrusionRecordDAO intrusionRecordDAO;
    private final BackgroundModelService backgroundService;
    private final DefenseZoneService defenseZoneService;
    private final IntrusionDetectionService intrusionDetectionService;
    private final RadarService radarService;
    private final AssemblyService assemblyService;
    private final PTZService ptzService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RadarController(RadarTestService radarTestService, Database database,
            BackgroundModelService backgroundService,
            DefenseZoneService defenseZoneService,
            IntrusionDetectionService intrusionDetectionService,
            RadarService radarService,
            AssemblyService assemblyService,
            PTZService ptzService) {
        this.radarTestService = radarTestService;
        this.database = database;
        this.connection = database.getConnection();
        this.radarDeviceDAO = new RadarDeviceDAO(connection);
        this.intrusionRecordDAO = new RadarIntrusionRecordDAO(connection);
        this.backgroundService = backgroundService;
        this.defenseZoneService = defenseZoneService;
        this.intrusionDetectionService = intrusionDetectionService;
        this.radarService = radarService;
        this.assemblyService = assemblyService;
        this.ptzService = ptzService;
    }

    /**
     * 测试雷达连接
     * POST /api/radar/test
     */
    public void testConnection(Context ctx) {
        try {
            if (radarTestService == null) {
                ctx.status(503);
                ctx.result(createErrorResponse(503, "雷达测试服务未启动，可能是Livox SDK依赖问题"));
                return;
            }
            String ip = ctx.queryParam("ip") != null ? ctx.queryParam("ip") : "192.168.1.115";
            RadarTestService.RadarDetectionResult result = radarTestService.testConnection(ip);

            Map<String, Object> payload = new HashMap<>();
            payload.put("reachable", result.isReachable());
            payload.put("message", result.getMessage());
            payload.put("ip", result.getIp());
            if (result.getRadarSerial() != null) {
                payload.put("radarSerial", result.getRadarSerial());
            }
            ctx.result(createSuccessResponse(payload));
        } catch (Exception e) {
            logger.error("测试雷达连接失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, e.getMessage()));
        }
    }

    // ==================== 雷达设备管理 ====================

    /**
     * 获取雷达设备列表
     * GET /api/radar/devices
     */
    public void getRadarDevices(Context ctx) {
        try {
            List<RadarDevice> devices = radarDeviceDAO.getAll();
            List<Map<String, Object>> deviceList = devices.stream()
                    .map(RadarDevice::toMap)
                    .collect(Collectors.toList());
            ctx.result(createSuccessResponse(deviceList));
        } catch (Exception e) {
            logger.error("获取雷达设备列表失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, e.getMessage()));
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
    public void addRadarDevice(Context ctx) {
        try {
            Map<String, Object> body = objectMapper.readValue(ctx.body(), new TypeReference<Map<String, Object>>() {});

            // 验证必填字段
            String radarIp = (String) body.get("radarIp");
            String radarName = (String) body.get("radarName");
            String radarSerial = (String) body.get("radarSerial");

            if (radarIp == null || radarIp.trim().isEmpty()) {
                ctx.status(400);
                ctx.result(createErrorResponse(400, "雷达IP地址不能为空"));
            }

            if (radarName == null || radarName.trim().isEmpty()) {
                ctx.status(400);
                ctx.result(createErrorResponse(400, "雷达名称不能为空"));
            }

            // 验证IP格式
            if (!isValidIpAddress(radarIp)) {
                ctx.status(400);
                ctx.result(createErrorResponse(400, "无效的IP地址格式"));
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
                ctx.status(400);
                ctx.result(createErrorResponse(400, "该IP地址已被其他设备使用: " + radarIp));
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
            String assemblyId = (String) body.get("assemblyId");
            device.setAssemblyId(assemblyId); // 可选
            device.setRadarSerial(radarSerial); // 可能为 null

            // 若指定了装置ID，需校验装置存在，避免外键约束失败
            if (assemblyId != null && !assemblyId.trim().isEmpty() && assemblyService != null) {
                Assembly assembly = assemblyService.getAssembly(assemblyId.trim());
                if (assembly == null) {
                    ctx.status(400);
                    ctx.result(createErrorResponse(400, "指定的装置不存在: " + assemblyId));
                }
                device.setAssemblyId(assemblyId.trim());
            }

            if (isUpdate) {
                RadarDevice existing = existingBySerial != null ? existingBySerial : existingByIp;
                if (existing != null) {
                    device.setStatus(existing.getStatus());
                    device.setCurrentBackgroundId(existing.getCurrentBackgroundId());
                }
            } else {
                device.setStatus(0);
            }

            // radar_devices 表有外键 device_id -> devices(device_id)，保存前需确保 devices 中已有对应记录
            DeviceInfo devInfo = new DeviceInfo();
            devInfo.setDeviceId(deviceId);
            devInfo.setIp(radarIp);
            devInfo.setPort(0);
            devInfo.setName(radarName.trim());
            devInfo.setUsername("");
            devInfo.setPassword("");
            devInfo.setRtspUrl("");
            devInfo.setStatus(device.getStatus());
            devInfo.setUserId(-1);
            devInfo.setChannel(1);
            devInfo.setBrand("radar");
            devInfo.setCameraType("radar");
            devInfo.setSerialNumber(radarSerial);
            if (!database.saveOrUpdateDevice(devInfo)) {
                logger.warn("同步 devices 表失败，继续尝试保存雷达设备: deviceId={}", deviceId);
            }

            if (radarDeviceDAO.saveOrUpdate(device)) {
                String snStatus = radarSerial != null ? "SN=" + radarSerial : "SN待自动填充";
                logger.info("雷达设备已{}: deviceId={}, radarIp={}, radarName={}, {}",
                        isUpdate ? "更新" : "添加",
                        deviceId, radarIp, radarName, snStatus);
                // 若指定了所属装置，同步加入 assembly_devices，便于装置详情页显示并启用坐标系标定
                if (assemblyService != null && device.getAssemblyId() != null && !device.getAssemblyId().trim().isEmpty()) {
                    try {
                        assemblyService.addDeviceToAssembly(device.getAssemblyId().trim(), deviceId, "radar", null);
                    } catch (Exception e) {
                        logger.warn("雷达关联装置时同步 assembly_devices 失败: assemblyId={}, deviceId={}", device.getAssemblyId(), deviceId, e);
                    }
                }
                Map<String, Object> result = device.toMap();
                if (radarSerial == null) {
                    result.put("snPending", true);
                    result.put("message", "设备已添加，SN将在雷达上线后自动填充");
                }
                ctx.result(createSuccessResponse(result));
            } else {
                ctx.status(400);
                ctx.result(createErrorResponse(400, isUpdate ? "更新雷达设备失败" : "添加雷达设备失败"));
            }
        } catch (Exception e) {
            logger.error("添加雷达设备失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, e.getMessage()));
        }
    }

    /**
     * 更新雷达设备
     * PUT /api/radar/devices/:deviceId
     */
    public void updateRadarDevice(Context ctx) {
        try {
            String deviceId = ctx.pathParam("deviceId");
            Map<String, Object> body = objectMapper.readValue(ctx.body(), new TypeReference<Map<String, Object>>() {});
            
            // 查找现有设备
            RadarDevice existingDevice = radarDeviceDAO.getByDeviceId(deviceId);
            if (existingDevice == null) {
                ctx.status(404);
                ctx.result(createErrorResponse(404, "雷达设备不存在: " + deviceId));
            }
            
            // 更新字段
            String radarIp = (String) body.get("radarIp");
            String radarName = (String) body.get("radarName");
            String assemblyId = (String) body.get("assemblyId");
            
            if (radarIp != null && !radarIp.trim().isEmpty()) {
                if (!isValidIpAddress(radarIp)) {
                    ctx.status(400);
                    ctx.result(createErrorResponse(400, "无效的IP地址格式"));
                }
                // 检查IP是否被其他设备占用
                List<RadarDevice> allDevices = radarDeviceDAO.getAll();
                for (RadarDevice d : allDevices) {
                    if (radarIp.equals(d.getRadarIp()) && !deviceId.equals(d.getDeviceId())) {
                        ctx.status(400);
                        ctx.result(createErrorResponse(400, "该IP地址已被其他设备使用: " + radarIp));
                    }
                }
                existingDevice.setRadarIp(radarIp.trim());
            }
            
            if (radarName != null && !radarName.trim().isEmpty()) {
                existingDevice.setRadarName(radarName.trim());
            }
            
            String previousAssemblyId = existingDevice.getAssemblyId();
            if (body.containsKey("assemblyId")) {
                existingDevice.setAssemblyId(assemblyId);
                // 若指定了装置ID，需校验装置存在，避免外键约束失败
                if (assemblyId != null && !assemblyId.trim().isEmpty() && assemblyService != null) {
                    Assembly assembly = assemblyService.getAssembly(assemblyId.trim());
                    if (assembly == null) {
                        ctx.status(400);
                        ctx.result(createErrorResponse(400, "指定的装置不存在: " + assemblyId));
                    }
                    existingDevice.setAssemblyId(assemblyId.trim());
                }
            }

            if (radarDeviceDAO.saveOrUpdate(existingDevice)) {
                // 同步 assembly_devices：从原装置移除、加入新装置（若有）
                if (assemblyService != null) {
                    if (previousAssemblyId != null && !previousAssemblyId.trim().isEmpty()) {
                        assemblyService.removeDeviceFromAssembly(previousAssemblyId.trim(), deviceId);
                    }
                    String newAssemblyId = existingDevice.getAssemblyId();
                    if (newAssemblyId != null && !newAssemblyId.trim().isEmpty()) {
                        try {
                            assemblyService.addDeviceToAssembly(newAssemblyId.trim(), deviceId, "radar", null);
                        } catch (Exception e) {
                            logger.warn("雷达关联装置时同步 assembly_devices 失败: assemblyId={}, deviceId={}", newAssemblyId, deviceId, e);
                        }
                    }
                }
                logger.info("雷达设备已更新: deviceId={}", deviceId);
                ctx.result(createSuccessResponse(existingDevice.toMap()));
            } else {
                ctx.status(400);
                ctx.result(createErrorResponse(400, "更新雷达设备失败"));
            }
        } catch (Exception e) {
            logger.error("更新雷达设备失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, e.getMessage()));
        }
    }

    /**
     * 删除雷达设备
     * DELETE /api/radar/devices/:deviceId
     * 级联删除所有关联数据：背景模型、防区、侵入记录
     */
    public void deleteRadarDevice(Context ctx) {
        try {
            String deviceId = ctx.pathParam("deviceId");
            
            // 查找现有设备
            RadarDevice existingDevice = radarDeviceDAO.getByDeviceId(deviceId);
            if (existingDevice == null) {
                ctx.status(404);
                ctx.result(createErrorResponse(404, "雷达设备不存在: " + deviceId));
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
                
                ctx.result(createSuccessResponse(result));
            } else {
                ctx.status(400);
                ctx.result(createErrorResponse(400, "删除雷达设备失败"));
            }
        } catch (Exception e) {
            logger.error("删除雷达设备失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, e.getMessage()));
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
    public void startBackgroundCollection(Context ctx) {
        try {
            if (radarService == null) {
                ctx.status(503);
                ctx.result(createErrorResponse(503, "雷达服务未启动，请先添加雷达设备"));
            }

            String deviceId = ctx.pathParam("deviceId");
            Map<String, Object> body = objectMapper.readValue(ctx.body(), new TypeReference<Map<String, Object>>() {});
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

            ctx.result(createSuccessResponse(result));
        } catch (Exception e) {
            logger.error("开始采集背景失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, e.getMessage()));
        }
    }

    /**
     * 停止采集背景
     * POST /api/radar/:deviceId/background/stop
     */
    public void stopBackgroundCollection(Context ctx) {
        try {
            if (radarService == null) {
                ctx.status(503);
                ctx.result(createErrorResponse(503, "雷达服务未启动，请先添加雷达设备"));
            }

            String deviceId = ctx.pathParam("deviceId");
            String backgroundId = radarService.stopBackgroundCollection(deviceId);

            if (backgroundId != null) {
                RadarBackground background = backgroundService.getBackgroundDAO().getByBackgroundId(backgroundId);
                Map<String, Object> result = new HashMap<>();
                result.put("backgroundId", backgroundId);
                result.put("status", "ready");
                result.put("frameCount", background != null ? background.getFrameCount() : 0);
                result.put("pointCount", background != null ? background.getPointCount() : 0);
                ctx.result(createSuccessResponse(result));
            } else {
                ctx.status(400);
                ctx.result(createErrorResponse(400, "停止采集失败"));
            }
        } catch (Exception e) {
            logger.error("停止采集背景失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, e.getMessage()));
        }
    }

    /**
     * 获取采集状态
     * GET /api/radar/:deviceId/background/status
     */
    public void getBackgroundStatus(Context ctx) {
        try {
            String deviceId = ctx.pathParam("deviceId");
            BackgroundModelService.CollectionStatus status = backgroundService.getCollectionStatus(deviceId);
            if (status != null) {
                ctx.result(createSuccessResponse(status));
            } else {
                ctx.status(404);
                ctx.result(createErrorResponse(404, "未找到采集状态"));
            }
        } catch (Exception e) {
            logger.error("获取采集状态失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, e.getMessage()));
        }
    }

    /**
     * 获取采集中的点云数据（用于实时预览）
     * GET /api/radar/:deviceId/background/collecting/points
     * 注意：实时点云数据应通过WebSocket获取，此接口仅用于采集过程中的预览
     */
    public void getCollectingPointCloud(Context ctx) {
        try {
            String deviceId = ctx.pathParam("deviceId");
            int maxPoints = ctx.queryParam("maxPoints") != null
                    ? Integer.parseInt(ctx.queryParam("maxPoints"))
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

            ctx.result(createSuccessResponse(result));
        } catch (Exception e) {
            logger.error("获取采集点云数据失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, e.getMessage()));
        }
    }

    /**
     * 获取背景点云数据（从文件读取）
     * GET /api/radar/:deviceId/background/:backgroundId/points
     */
    public void getBackgroundPoints(Context ctx) {
        try {
            String backgroundId = ctx.pathParam("backgroundId");
            int maxPoints = ctx.queryParam("maxPoints") != null
                    ? Integer.parseInt(ctx.queryParam("maxPoints"))
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

            ctx.result(createSuccessResponse(result));
        } catch (Exception e) {
            logger.error("获取背景点云数据失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, e.getMessage()));
        }
    }

    // ==================== 防区管理 ====================

    /**
     * 获取防区列表
     * GET /api/radar/:deviceId/zones
     */
    public void getZones(Context ctx) {
        try {
            String deviceId = ctx.pathParam("deviceId");
            List<DefenseZone> zones = defenseZoneService.getZonesByDeviceId(deviceId);
            List<Map<String, Object>> zoneList = zones.stream()
                    .map(this::convertZoneToMap)
                    .collect(Collectors.toList());
            ctx.result(createSuccessResponse(zoneList));
        } catch (Exception e) {
            logger.error("获取防区列表失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, e.getMessage()));
        }
    }

    /**
     * 创建防区
     * POST /api/radar/:deviceId/zones
     */
    public void createZone(Context ctx) {
        try {
            String deviceId = ctx.pathParam("deviceId");
            Map<String, Object> body = objectMapper.readValue(ctx.body(), new TypeReference<Map<String, Object>>() {});

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
                ctx.result(createSuccessResponse(convertZoneToMap(defenseZoneService.getZone(zoneId))));
            } else {
                ctx.status(400);
                ctx.result(createErrorResponse(400, "创建防区失败"));
            }
        } catch (Exception e) {
            logger.error("创建防区失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, e.getMessage()));
        }
    }

    /**
     * 更新防区
     * PUT /api/radar/:deviceId/zones/:zoneId
     */
    public void updateZone(Context ctx) {
        try {
            String deviceId = ctx.pathParam("deviceId");
            String zoneId = ctx.pathParam("zoneId");
            Map<String, Object> body = objectMapper.readValue(ctx.body(), new TypeReference<Map<String, Object>>() {});

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
                ctx.result(createSuccessResponse(convertZoneToMap(defenseZoneService.getZone(zoneId))));
            } else {
                ctx.status(400);
                ctx.result(createErrorResponse(400, "更新防区失败"));
            }
        } catch (Exception e) {
            logger.error("更新防区失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, e.getMessage()));
        }
    }

    /**
     * 删除防区
     * DELETE /api/radar/:deviceId/zones/:zoneId
     */
    public void deleteZone(Context ctx) {
        try {
            String deviceId = ctx.pathParam("deviceId");
            String zoneId = ctx.pathParam("zoneId");
            if (defenseZoneService.deleteZone(zoneId)) {
                // 重新加载检测上下文
                radarService.reloadDeviceDetectionContext(deviceId);
                ctx.result(createSuccessResponse(null));
            } else {
                ctx.status(400);
                ctx.result(createErrorResponse(400, "删除防区失败"));
            }
        } catch (Exception e) {
            logger.error("删除防区失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, e.getMessage()));
        }
    }

    /**
     * 获取当前侵入检测是否开启
     * GET /api/radar/:deviceId/detection
     */
    public void getDetectionEnabled(Context ctx) {
        try {
            String deviceId = ctx.pathParam("deviceId");
            String state = radarService.getDeviceState(deviceId);
            boolean enabled = "detecting".equals(state);

            Map<String, Object> payload = new HashMap<>();
            payload.put("deviceId", deviceId);
            payload.put("detectionEnabled", enabled);
            ctx.result(createSuccessResponse(payload));
        } catch (Exception e) {
            logger.error("获取检测状态失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, e.getMessage()));
        }
    }

    /**
     * 开启或关闭侵入检测
     * PUT /api/radar/:deviceId/detection
     * Body: { "enabled": true } 开启检测，{ "enabled": false } 关闭检测（仅推送点云，不跑侵入检测，避免队列满）
     */
    public void setDetectionEnabled(Context ctx) {
        try {
            String deviceId = ctx.pathParam("deviceId");
            Map<String, Object> body = objectMapper.readValue(ctx.body(), new TypeReference<Map<String, Object>>() {});
            Boolean enabled = body.get("enabled") != null && Boolean.TRUE.equals(body.get("enabled"));

            if (enabled) {
                radarService.reloadDeviceDetectionContext(deviceId);
                radarService.setDeviceState(deviceId, "detecting");
                logger.info("开启侵入检测: deviceId={}", deviceId);
            } else {
                radarService.setDeviceState(deviceId, "");
                logger.info("关闭侵入检测: deviceId={}", deviceId);
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("deviceId", deviceId);
            payload.put("detectionEnabled", enabled);
            ctx.result(createSuccessResponse(payload));
        } catch (Exception e) {
            logger.error("设置检测状态失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, e.getMessage()));
        }
    }

    /**
     * 切换防区启用状态
     * PUT /api/radar/:deviceId/zones/:zoneId/toggle
     */
    public void toggleZone(Context ctx) {
        try {
            String zoneId = ctx.pathParam("zoneId");
            DefenseZone zone = defenseZoneService.getZone(zoneId);
            if (zone == null) {
                ctx.status(404);
                ctx.result(createErrorResponse(404, "防区不存在"));
            }

            zone.setEnabled(!zone.getEnabled());
            if (defenseZoneService.updateZone(zoneId, zone)) {
                // 重新加载检测上下文
                String deviceId = ctx.pathParam("deviceId");
                radarService.reloadDeviceDetectionContext(deviceId);
                ctx.result(createSuccessResponse(convertZoneToMap(defenseZoneService.getZone(zoneId))));
            } else {
                ctx.status(400);
                ctx.result(createErrorResponse(400, "切换防区状态失败"));
            }
        } catch (Exception e) {
            logger.error("切换防区状态失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, e.getMessage()));
        }
    }

    /**
     * 获取背景模型列表
     * GET /api/radar/:deviceId/backgrounds
     */
    public void getBackgrounds(Context ctx) {
        try {
            String deviceId = ctx.pathParam("deviceId");
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
            ctx.result(createSuccessResponse(backgroundList));
        } catch (Exception e) {
            logger.error("获取背景列表失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, e.getMessage()));
        }
    }

    /**
     * 删除背景模型
     * DELETE /api/radar/:deviceId/backgrounds/:backgroundId
     */
    public void deleteBackground(Context ctx) {
        try {
            String deviceId = ctx.pathParam("deviceId");
            String backgroundId = ctx.pathParam("backgroundId");

            // 删除数据库记录和文件
            boolean deleted = backgroundService.deleteBackground(backgroundId);

            if (deleted) {
                logger.info("背景模型删除成功: deviceId={}, backgroundId={}", deviceId, backgroundId);
                ctx.result(createSuccessResponse(null));
            } else {
                ctx.status(400);
                ctx.result(createErrorResponse(400, "删除背景模型失败"));
            }
        } catch (Exception e) {
            logger.error("删除背景模型失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, e.getMessage()));
        }
    }

    // ==================== 侵入记录 ====================

    /**
     * 获取侵入记录列表
     * GET /api/radar/:deviceId/intrusions
     */
    public void getIntrusions(Context ctx) {
        try {
            String deviceId = ctx.pathParam("deviceId");
            String zoneId = ctx.queryParam("zoneId");
            String startTimeStr = ctx.queryParam("startTime");
            String endTimeStr = ctx.queryParam("endTime");
            int page = ctx.queryParam("page") != null ? Integer.parseInt(ctx.queryParam("page")) : 1;
            int pageSize = ctx.queryParam("pageSize") != null ? Integer.parseInt(ctx.queryParam("pageSize"))
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

            ctx.result(createSuccessResponse(result));
        } catch (Exception e) {
            logger.error("获取侵入记录失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, e.getMessage()));
        }
    }

    /**
     * 清空侵入记录
     * DELETE /api/radar/:deviceId/intrusions
     */
    public void clearIntrusions(Context ctx) {
        try {
            String deviceId = ctx.pathParam("deviceId");
            int count = intrusionDetectionService.clearIntrusionRecords(deviceId);
            Map<String, Object> result = new HashMap<>();
            result.put("deletedCount", count);
            logger.info("清空侵入记录: deviceId={}, 删除数量={}", deviceId, count);
            ctx.result(createSuccessResponse(result));
        } catch (Exception e) {
            logger.error("清空侵入记录失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, e.getMessage()));
        }
    }

    /**
     * 获取侵入数据文件
     * GET /api/radar/intrusions/:id/data
     */
    public void getIntrusionData(Context ctx) {
        try {
            String id = ctx.pathParam("id");
            RadarIntrusionRecord record = intrusionRecordDAO.getById(id);
            if (record == null) {
                ctx.status(404);
                ctx.result(createErrorResponse(404, "记录不存在"));
            }

            String clusterId = record.getClusterId();
            if (clusterId == null || !clusterId.startsWith("TRAJECTORY:")) {
                ctx.status(404);
                ctx.result(createErrorResponse(404, "该记录没有轨迹数据"));
            }

            String relativePath = clusterId.substring("TRAJECTORY:".length());
            java.io.File file = new java.io.File(relativePath);
            if (!file.exists()) {
                ctx.status(404);
                ctx.result(createErrorResponse(404, "数据文件未找到"));
            }

            // 读取JSON文件并解析为Map，然后包装在标准响应格式中
            String jsonContent = new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            Map<String, Object> recordData = objectMapper.readValue(jsonContent, new TypeReference<Map<String, Object>>() {});
            ctx.result(createSuccessResponse(recordData));
        } catch (Exception e) {
            logger.error("读取侵入数据文件失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, e.getMessage()));
        }
    }

    // ==================== 白名单（空间排除区）管理 ====================

    /**
     * 获取防区白名单
     * GET /api/radar/:deviceId/zones/:zoneId/whitelist
     */
    public void getWhitelist(Context ctx) {
        try {
            String zoneId = ctx.pathParam("zoneId");
            PointCloudProcessCenter center = radarService.getPointCloudProcessCenter();
            if (center == null) {
                ctx.status(503);
                ctx.result(createErrorResponse(503, "点云处理中心未初始化"));
                return;
            }
            List<com.digital.video.gateway.driver.livox.model.ExclusionZone> list = center.getWhitelist(zoneId);
            List<Map<String, Object>> result = new ArrayList<>();
            for (com.digital.video.gateway.driver.livox.model.ExclusionZone ez : list) {
                result.add(convertExclusionZoneToMap(ez));
            }
            ctx.result(createSuccessResponse(result));
        } catch (Exception e) {
            logger.error("获取白名单失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, e.getMessage()));
        }
    }

    /**
     * 添加白名单（通过跟踪目标 ID 或手动指定空间范围）
     * POST /api/radar/:deviceId/zones/:zoneId/whitelist
     *
     * Body:
     *   { "trackingId": "trk_1" }        — 自动从当前跟踪目标提取空间
     *   { "label":"...", "minX":..., "maxX":..., ... }  — 手动指定
     */
    public void addWhitelist(Context ctx) {
        try {
            String zoneId = ctx.pathParam("zoneId");
            PointCloudProcessCenter center = radarService.getPointCloudProcessCenter();
            if (center == null) {
                ctx.status(503);
                ctx.result(createErrorResponse(503, "点云处理中心未初始化"));
                return;
            }

            Map<String, Object> body = objectMapper.readValue(ctx.body(), new TypeReference<Map<String, Object>>() {});
            com.digital.video.gateway.driver.livox.model.ExclusionZone ez;

            if (body.containsKey("trackingId")) {
                String trackingId = (String) body.get("trackingId");
                ez = center.addWhitelistByTrackingId(zoneId, trackingId);
                if (ez == null) {
                    ctx.status(404);
                    ctx.result(createErrorResponse(404, "未找到跟踪目标: " + trackingId));
                    return;
                }
            } else {
                String label = body.get("label") != null ? (String) body.get("label") : "手动排除区";
                float minX = ((Number) body.get("minX")).floatValue();
                float maxX = ((Number) body.get("maxX")).floatValue();
                float minY = ((Number) body.get("minY")).floatValue();
                float maxY = ((Number) body.get("maxY")).floatValue();
                float minZ = ((Number) body.get("minZ")).floatValue();
                float maxZ = ((Number) body.get("maxZ")).floatValue();
                ez = center.addWhitelistManual(zoneId, label, minX, maxX, minY, maxY, minZ, maxZ);
            }

            ctx.result(createSuccessResponse(convertExclusionZoneToMap(ez)));
        } catch (Exception e) {
            logger.error("添加白名单失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, e.getMessage()));
        }
    }

    /**
     * 删除指定白名单条目
     * DELETE /api/radar/:deviceId/zones/:zoneId/whitelist/:exclusionId
     */
    public void removeWhitelist(Context ctx) {
        try {
            String zoneId = ctx.pathParam("zoneId");
            String exclusionId = ctx.pathParam("exclusionId");
            PointCloudProcessCenter center = radarService.getPointCloudProcessCenter();
            if (center == null) {
                ctx.status(503);
                ctx.result(createErrorResponse(503, "点云处理中心未初始化"));
                return;
            }
            if (center.removeWhitelistEntry(zoneId, exclusionId)) {
                ctx.result(createSuccessResponse("已移除"));
            } else {
                ctx.status(404);
                ctx.result(createErrorResponse(404, "白名单条目不存在"));
            }
        } catch (Exception e) {
            logger.error("删除白名单失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, e.getMessage()));
        }
    }

    /**
     * 清空防区所有白名单
     * DELETE /api/radar/:deviceId/zones/:zoneId/whitelist
     */
    public void clearWhitelist(Context ctx) {
        try {
            String zoneId = ctx.pathParam("zoneId");
            PointCloudProcessCenter center = radarService.getPointCloudProcessCenter();
            if (center == null) {
                ctx.status(503);
                ctx.result(createErrorResponse(503, "点云处理中心未初始化"));
                return;
            }
            int count = center.clearWhitelist(zoneId);
            Map<String, Object> data = new HashMap<>();
            data.put("cleared", count);
            ctx.result(createSuccessResponse(data));
        } catch (Exception e) {
            logger.error("清空白名单失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, e.getMessage()));
        }
    }

    /**
     * 获取当前活跃的跟踪目标列表（供前端选择加白名单）
     * GET /api/radar/:deviceId/zones/:zoneId/targets
     */
    public void getActiveTargets(Context ctx) {
        try {
            String zoneId = ctx.pathParam("zoneId");
            PointCloudProcessCenter center = radarService.getPointCloudProcessCenter();
            if (center == null) {
                ctx.status(503);
                ctx.result(createErrorResponse(503, "点云处理中心未初始化"));
                return;
            }
            List<Map<String, Object>> targets = center.getActiveTargets(zoneId);
            ctx.result(createSuccessResponse(targets));
        } catch (Exception e) {
            logger.error("获取活跃目标失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, e.getMessage()));
        }
    }

    /**
     * 获取当前防区内最显著目标坐标（用于标定采集）
     * GET /api/radar/:deviceId/calibration/target?zoneId=xxx
     */
    public void getCalibrationTarget(Context ctx) {
        try {
            String deviceId = ctx.pathParam("deviceId");
            String zoneId = ctx.queryParam("zoneId");
            if (zoneId == null || zoneId.trim().isEmpty()) {
                ctx.status(400);
                ctx.result(createErrorResponse(400, "缺少 zoneId 参数"));
                return;
            }
            PointCloudProcessCenter center = radarService.getPointCloudProcessCenter();
            if (center == null) {
                ctx.status(503);
                ctx.result(createErrorResponse(503, "点云处理中心未初始化"));
                return;
            }
            List<Map<String, Object>> targets = center.getActiveTargets(zoneId);
            if (targets == null || targets.isEmpty()) {
                ctx.status(200);
                ctx.result(createSuccessResponse(null));
                return;
            }
            Map<String, Object> nearest = targets.stream()
                    .min(Comparator.comparingDouble(t -> ((Number) t.getOrDefault("distance", Double.MAX_VALUE)).doubleValue()))
                    .orElse(null);
            if (nearest == null) {
                ctx.result(createSuccessResponse(null));
                return;
            }
            Map<String, Object> pos = new HashMap<>();
            pos.put("radarX", nearest.get("centroidX"));
            pos.put("radarY", nearest.get("centroidY"));
            pos.put("radarZ", nearest.get("centroidZ"));
            ctx.result(createSuccessResponse(pos));
        } catch (Exception e) {
            logger.error("获取标定目标失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, e.getMessage()));
        }
    }

    /**
     * 根据标定点计算坐标变换参数
     * POST /api/radar/:deviceId/calibration/compute
     * Body: { "zoneId": "...", "points": [ { "radarX", "radarY", "radarZ", "cameraPan", "cameraTilt" }, ... ] }
     */
    @SuppressWarnings("unchecked")
    public void postCalibrationCompute(Context ctx) {
        try {
            String deviceId = ctx.pathParam("deviceId");
            Map<String, Object> body = objectMapper.readValue(ctx.body(), Map.class);
            String zoneId = (String) body.get("zoneId");
            List<Map<String, Object>> pointsList = (List<Map<String, Object>>) body.get("points");
            if (zoneId == null || pointsList == null || pointsList.isEmpty()) {
                ctx.status(400);
                ctx.result(createErrorResponse(400, "缺少 zoneId 或 points"));
                return;
            }
            List<CalibrationSolver.CalibrationPoint> points = new ArrayList<>();
            for (Map<String, Object> p : pointsList) {
                float rx = numberToFloat(p.get("radarX"));
                float ry = numberToFloat(p.get("radarY"));
                float rz = numberToFloat(p.get("radarZ"));
                float pan = numberToFloat(p.get("cameraPan"));
                float tilt = numberToFloat(p.get("cameraTilt"));
                points.add(new CalibrationSolver.CalibrationPoint(rx, ry, rz, pan, tilt));
            }
            CalibrationSolver.CalibrationResult result = CalibrationSolver.solve(points);
            if (result == null) {
                ctx.status(400);
                ctx.result(createErrorResponse(400, "标定求解失败"));
                return;
            }
            Map<String, Object> transform = new HashMap<>();
            Map<String, Object> trans = new HashMap<>();
            trans.put("x", result.translationX);
            trans.put("y", result.translationY);
            trans.put("z", result.translationZ);
            transform.put("translation", trans);
            Map<String, Object> rot = new HashMap<>();
            rot.put("x", result.rotationX);
            rot.put("y", result.rotationY);
            rot.put("z", result.rotationZ);
            transform.put("rotation", rot);
            transform.put("scale", result.scale);
            Map<String, Object> error = new HashMap<>();
            error.put("avgDegrees", result.avgErrorDegrees);
            error.put("maxDegrees", result.maxErrorDegrees);
            error.put("perPointPan", result.perPointPanErrorDegrees);
            error.put("perPointTilt", result.perPointTiltErrorDegrees);
            Map<String, Object> data = new HashMap<>();
            data.put("transform", transform);
            data.put("error", error);
            ctx.status(200);
            ctx.result(createSuccessResponse(data));
        } catch (Exception e) {
            logger.error("标定计算失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, e.getMessage()));
        }
    }

    /**
     * 使用给定变换参数驱动球机瞄准指定雷达坐标（验证标定）
     * POST /api/radar/:deviceId/calibration/verify
     * Body: { "zoneId": "...", "transform": { translation, rotation, scale }, "radarX", "radarY", "radarZ" }
     */
    @SuppressWarnings("unchecked")
    public void postCalibrationVerify(Context ctx) {
        try {
            String deviceId = ctx.pathParam("deviceId");
            Map<String, Object> body = objectMapper.readValue(ctx.body(), Map.class);
            String zoneId = (String) body.get("zoneId");
            Map<String, Object> transformMap = (Map<String, Object>) body.get("transform");
            float rx = numberToFloat(body.get("radarX"));
            float ry = numberToFloat(body.get("radarY"));
            float rz = numberToFloat(body.get("radarZ"));
            if (zoneId == null || transformMap == null) {
                ctx.status(400);
                ctx.result(createErrorResponse(400, "缺少 zoneId 或 transform"));
                return;
            }
            DefenseZone zone = defenseZoneService.getZone(zoneId);
            if (zone == null) {
                ctx.status(404);
                ctx.result(createErrorResponse(404, "防区不存在"));
                return;
            }
            String cameraDeviceId = zone.getCameraDeviceId();
            if (cameraDeviceId == null || cameraDeviceId.trim().isEmpty()) {
                ctx.status(400);
                ctx.result(createErrorResponse(400, "防区未关联球机"));
                return;
            }
            int channel = zone.getCameraChannel() != null ? zone.getCameraChannel() : 1;
            CoordinateTransform t = mapToCoordinateTransform(transformMap);
            Point radarPoint = new Point(rx, ry, rz);
            Point cameraPoint = t.transformRadarToCamera(radarPoint);
            float[] angles = t.calculatePTZAngles(cameraPoint);
            float pan = angles[0];
            float tilt = angles[1];
            float zoom = 1.0f;
            boolean ok = ptzService.gotoAngle(cameraDeviceId, channel, pan, tilt, zoom);
            Map<String, Object> data = new HashMap<>();
            data.put("pan", pan);
            data.put("tilt", tilt);
            data.put("zoom", zoom);
            data.put("success", ok);
            ctx.status(200);
            ctx.result(createSuccessResponse(data));
        } catch (Exception e) {
            logger.error("标定验证失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, e.getMessage()));
        }
    }

    /**
     * 将标定结果写入防区配置
     * POST /api/radar/:deviceId/calibration/apply
     * Body: { "zoneId": "...", "transform": { translation, rotation, scale } }
     */
    @SuppressWarnings("unchecked")
    public void postCalibrationApply(Context ctx) {
        try {
            String deviceId = ctx.pathParam("deviceId");
            Map<String, Object> body = objectMapper.readValue(ctx.body(), Map.class);
            String zoneId = (String) body.get("zoneId");
            Map<String, Object> transformMap = (Map<String, Object>) body.get("transform");
            if (zoneId == null || transformMap == null) {
                ctx.status(400);
                ctx.result(createErrorResponse(400, "缺少 zoneId 或 transform"));
                return;
            }
            DefenseZone zone = defenseZoneService.getZone(zoneId);
            if (zone == null) {
                ctx.status(404);
                ctx.result(createErrorResponse(404, "防区不存在"));
                return;
            }
            String coordinateTransformJson = objectMapper.writeValueAsString(transformMap);
            zone.setCoordinateTransformJson(coordinateTransformJson);
            boolean updated = defenseZoneService.updateZone(zoneId, zone);
            if (!updated) {
                ctx.status(500);
                ctx.result(createErrorResponse(500, "保存防区失败"));
                return;
            }
            ctx.status(200);
            ctx.result(createSuccessResponse(convertZoneToMap(defenseZoneService.getZone(zoneId))));
        } catch (Exception e) {
            logger.error("标定应用失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, e.getMessage()));
        }
    }

    private static float numberToFloat(Object o) {
        if (o == null) return 0f;
        if (o instanceof Number) return ((Number) o).floatValue();
        if (o instanceof String) return Float.parseFloat((String) o);
        return 0f;
    }

    @SuppressWarnings("unchecked")
    private static CoordinateTransform mapToCoordinateTransform(Map<String, Object> transformMap) {
        float tx = 0, ty = 0, tz = 0, rx = 0, ry = 0, rz = 0, scale = 1f;
        if (transformMap.get("translation") instanceof Map) {
            Map<String, Object> trans = (Map<String, Object>) transformMap.get("translation");
            tx = numberToFloat(trans.get("x"));
            ty = numberToFloat(trans.get("y"));
            tz = numberToFloat(trans.get("z"));
        }
        if (transformMap.get("rotation") instanceof Map) {
            Map<String, Object> rot = (Map<String, Object>) transformMap.get("rotation");
            rx = numberToFloat(rot.get("x"));
            ry = numberToFloat(rot.get("y"));
            rz = numberToFloat(rot.get("z"));
        }
        scale = numberToFloat(transformMap.get("scale"));
        if (scale <= 0) scale = 1f;
        return new CoordinateTransform(tx, ty, tz, rx, ry, rz, scale);
    }

    private Map<String, Object> convertExclusionZoneToMap(com.digital.video.gateway.driver.livox.model.ExclusionZone ez) {
        Map<String, Object> map = new HashMap<>();
        map.put("exclusionId", ez.getExclusionId());
        map.put("zoneId", ez.getZoneId());
        map.put("sourceTrackingId", ez.getSourceTrackingId());
        map.put("label", ez.getLabel());
        map.put("minX", ez.getMinX());
        map.put("maxX", ez.getMaxX());
        map.put("minY", ez.getMinY());
        map.put("maxY", ez.getMaxY());
        map.put("minZ", ez.getMinZ());
        map.put("maxZ", ez.getMaxZ());
        map.put("createdAt", ez.getCreatedAt());
        return map;
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

    /**
     * PTZ 抑制（标定模式）
     * PUT /api/radar/ptz/suppress
     * Body: { "cameraDeviceId": "xxx", "suppress": true/false }
     */
    public void setPtzSuppress(Context ctx) {
        try {
            Map<String, Object> body = objectMapper.readValue(ctx.body(), new TypeReference<Map<String, Object>>() {});
            String cameraDeviceId = (String) body.get("cameraDeviceId");
            Boolean suppress = body.get("suppress") != null && Boolean.TRUE.equals(body.get("suppress"));

            if (cameraDeviceId == null || cameraDeviceId.trim().isEmpty()) {
                ctx.status(400);
                ctx.result(createErrorResponse(400, "缺少 cameraDeviceId"));
                return;
            }

            PointCloudProcessCenter center = radarService.getPointCloudProcessCenter();
            if (center == null) {
                ctx.status(503);
                ctx.result(createErrorResponse(503, "点云处理中心未初始化"));
                return;
            }

            if (suppress) {
                center.suppressPtz(cameraDeviceId);
            } else {
                center.unsuppressPtz(cameraDeviceId);
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("cameraDeviceId", cameraDeviceId);
            payload.put("suppressed", suppress);
            ctx.result(createSuccessResponse(payload));
        } catch (Exception e) {
            logger.error("设置PTZ抑制失败", e);
            ctx.status(500);
            ctx.result(createErrorResponse(500, e.getMessage()));
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
