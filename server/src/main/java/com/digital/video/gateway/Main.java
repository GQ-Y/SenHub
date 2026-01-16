package com.digital.video.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.digital.video.gateway.api.*;
import com.digital.video.gateway.auth.AuthFilter;
import com.digital.video.gateway.command.CommandHandler;
import com.digital.video.gateway.command.CommandResponse;
import com.digital.video.gateway.config.Config;
import com.digital.video.gateway.config.ConfigLoader;
import com.digital.video.gateway.database.Database;
import com.digital.video.gateway.database.DeviceInfo;
import com.digital.video.gateway.device.DeviceManager;
import com.digital.video.gateway.device.SDKFactory;
import com.digital.video.gateway.hikvision.HikvisionSDK;
import com.digital.video.gateway.service.*;
import com.digital.video.gateway.keeper.Keeper;
import com.digital.video.gateway.mqtt.MqttClient;
import com.digital.video.gateway.recorder.Recorder;
import com.digital.video.gateway.scanner.DeviceScanner;
import com.digital.video.gateway.service.ConfigService;
import com.digital.video.gateway.service.RecorderService;
import com.digital.video.gateway.service.CaptureService;
import com.digital.video.gateway.service.PTZService;
import com.digital.video.gateway.service.PlaybackService;
import com.digital.video.gateway.service.AlarmService;
import com.digital.video.gateway.service.OssService;
import com.digital.video.gateway.service.AssemblyService;
import com.digital.video.gateway.service.AlarmRuleService;
import com.digital.video.gateway.service.AlarmRecordService;
import com.digital.video.gateway.service.SpeakerService;
import com.digital.video.gateway.service.RecordingTaskService;
import com.digital.video.gateway.database.RadarDeviceDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 综合性数字视频监控网关系统主程序
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private Config config;
    private Database database;
    private MqttClient mqttClient;
    private DeviceManager deviceManager;
    private DeviceScanner scanner;
    private CommandHandler commandHandler;
    private Keeper keeper;
    private Recorder recorder;
    private ConfigService configService;
    private RecorderService recorderService;
    private CaptureService captureService;
    private PTZService ptzService;
    private PlaybackService playbackService;
    private OssService ossService;
    private AlarmService alarmService;
    private AssemblyService assemblyService;
    private AlarmRuleService alarmRuleService;
    private AlarmRecordService alarmRecordService;
    private SpeakerService speakerService;
    private RecordingTaskService recordingTaskService;
    private RadarService radarService;
    private RadarTestService radarTestService;
    private RadarController radarController;
    private ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) {
        Main main = new Main();
        main.start();
    }

    /**
     * 启动服务
     */
    public void start() {
        logger.info("启动综合性数字视频监控网关系统...");

        try {
            // 1. 加载配置
            config = ConfigLoader.load("config.yaml");
            if (config == null) {
                logger.error("配置文件加载失败");
                System.exit(1);
            }
            logger.info("配置文件加载成功");

            // 2. 初始化多品牌SDK工厂
            SDKFactory.init(config);
            logger.info("多品牌SDK工厂初始化完成");

            // 3. 初始化数据库
            database = new Database(config.getDatabase().getPath());
            if (!database.init()) {
                logger.error("数据库初始化失败");
                System.exit(1);
            }
            logger.info("数据库初始化成功");

            // 3.5. 初始化配置服务（混合模式：数据库优先，YAML作为默认值）
            configService = new ConfigService(database, config);
            config = configService.getConfig(); // 使用从数据库加载的配置
            logger.info("配置服务初始化成功");

            // 3.6. 使用配置中的SDK信息初始化默认驱动
            if (config.getSdk() != null) {
                Config.SdkConfig sdkConfig = config.getSdk();
                database.initDefaultDriverWithConfig(
                        "hikvision_sdk",
                        "Hikvision SDK",
                        "6.1.9.45",
                        sdkConfig.getLibPath() != null ? sdkConfig.getLibPath() : "./lib",
                        sdkConfig.getLogPath() != null ? sdkConfig.getLogPath() : "./sdkLog",
                        sdkConfig.getLogLevel() > 0 ? sdkConfig.getLogLevel() : 3,
                        "ACTIVE");
                logger.info("默认SDK驱动配置已初始化");
            }

            // 4. 初始化设备管理器
            deviceManager = new DeviceManager(database, config.getDevice());
            logger.info("设备管理器初始化成功");

            // 5. 初始化功能服务类
            recorderService = new RecorderService(deviceManager, config.getRecorder());
            captureService = new CaptureService(deviceManager, "./storage/captures");
            ptzService = new PTZService(deviceManager);
            playbackService = new PlaybackService(deviceManager);

            // 5.5. 初始化OSS服务
            ossService = new OssService(config.getOss());
            logger.info("OSS服务初始化完成，状态: {}", ossService.isEnabled() ? "已启用" : "未启用");

            // 5.6. 初始化报警服务
            alarmService = new AlarmService(deviceManager, captureService, ossService);
            logger.info("报警服务初始化成功");

            // 5.7. 初始化新增服务
            assemblyService = new AssemblyService(database);
            alarmRuleService = new AlarmRuleService(database);
            alarmRecordService = new AlarmRecordService(database);
            speakerService = new SpeakerService(database);
            recordingTaskService = new RecordingTaskService(database, deviceManager, ossService);

            // 检查数据库是否有雷达设备，没有则不启动雷达服务
            RadarDeviceDAO radarDeviceDAO = new RadarDeviceDAO(database.getConnection());
            List<com.digital.video.gateway.database.RadarDevice> radarDevices = radarDeviceDAO.getAll();
            if (radarDevices.isEmpty()) {
                logger.info("数据库中没有雷达设备，跳过雷达服务启动");
                radarService = null; // 设置为null，避免后续使用
            } else {
                radarService = new RadarService(ptzService, database);
                radarService.start();
                logger.info("雷达服务已启动，配置了 {} 个雷达设备", radarDevices.size());
            }

            radarTestService = new RadarTestService();
            logger.info("新增服务初始化成功");

            // 5.8. 注入依赖到AlarmService
            alarmService.setAlarmRuleService(alarmRuleService);
            alarmService.setAlarmRecordService(alarmRecordService);
            alarmService.setRecordingTaskService(recordingTaskService);
            alarmService.setSpeakerService(speakerService);
            alarmService.setPTZService(ptzService);
            alarmService.setMqttClient(mqttClient);
            alarmService.setDatabase(database);
            logger.info("AlarmService依赖注入完成");

            logger.info("功能服务类初始化成功");

            // 6. 初始化录制管理器（使用RecorderService）
            recorder = new Recorder(recorderService, config.getRecorder());
            recorder.start();
            logger.info("录制管理器初始化成功");

            // 7. 初始化MQTT客户端（MQTT连接失败不应阻止服务启动）
            mqttClient = new MqttClient(config.getMqtt());
            setupMqttMessageHandler();
            if (!mqttClient.connect()) {
                logger.warn("MQTT连接失败，服务将继续运行但MQTT功能不可用（可以稍后通过API重启MQTT连接）");
                // 不退出服务，允许HTTP服务器正常启动
            } else {
                logger.info("MQTT客户端连接成功");
            }

            // 7.5. 设置DeviceManager的MQTT客户端（用于状态通知）
            deviceManager.setMqttClient(mqttClient);
            logger.info("DeviceManager已设置MQTT客户端");

            // 7.6. 设置SDK状态回调（用于设备离线/在线监听）
            HikvisionSDK hikvisionSDK = (HikvisionSDK) SDKFactory.getSDK("hikvision");
            if (hikvisionSDK != null) {
                hikvisionSDK.setStatusCallbacks(deviceManager, mqttClient);
                hikvisionSDK.setAlarmService(alarmService);
                logger.info("海康SDK状态回调和报警回调已设置");
            }

            com.digital.video.gateway.dahua.DahuaSDK dahuaSDK = (com.digital.video.gateway.dahua.DahuaSDK) SDKFactory
                    .getSDK("dahua");
            if (dahuaSDK != null) {
                dahuaSDK.setStatusCallbacks(deviceManager, mqttClient);
                logger.info("大华SDK状态回调已设置");
            }

            // 8. 初始化命令处理器（使用功能服务类）
            commandHandler = new CommandHandler(deviceManager, hikvisionSDK, recorder,
                    captureService, ptzService, playbackService);
            logger.info("命令处理器初始化成功");

            // 9. 启动设备扫描器（暂时使用海康SDK，后续可扩展为多品牌）
            scanner = new DeviceScanner(hikvisionSDK, database, config.getScanner(), config.getDevice());
            scanner.setDeviceFoundCallback(this::onDeviceFound);
            if (scanner.start()) {
                logger.info("设备扫描器启动成功");
            }

            // 10. 启动保活系统
            keeper = new Keeper(deviceManager, config.getKeeper(), recorder);
            keeper.start();
            logger.info("保活系统启动成功");

            // 11. 启动HTTP服务器（先启动HTTP服务器，确保API接口可用）
            startHttpServer();
            logger.info("HTTP服务器启动成功");

            // 11.5. 为所有已存在的设备启动录制（如果录制功能启用，异步执行避免阻塞）
            if (recorder != null && config.getRecorder() != null && config.getRecorder().isEnabled()) {
                // 在后台线程中执行，避免阻塞主线程
                new Thread(() -> {
                    try {
                        Thread.sleep(2000); // 等待2秒，确保HTTP服务器完全启动
                        startRecordingForExistingDevices();
                    } catch (Exception e) {
                        logger.error("后台启动设备录制失败", e);
                    }
                }, "DeviceRecordingStarter").start();
            }

            // 12. 注册JVM关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

            logger.info("========================================");
            logger.info("综合性数字视频监控网关系统启动成功！");
            logger.info("========================================");

            // 主线程保持运行
            Thread.currentThread().join();

        } catch (Exception e) {
            logger.error("服务启动失败", e);
            shutdown();
            System.exit(1);
        }
    }

    /**
     * 设置MQTT消息处理器
     */
    private void setupMqttMessageHandler() {
        mqttClient.setMessageHandler(message -> {
            try {
                logger.info("收到MQTT命令: {}", message);
                CommandResponse response = commandHandler.handleCommand(message);

                // 发布响应
                String responseJson = objectMapper.writeValueAsString(response);
                mqttClient.publishResponse(responseJson);
                logger.info("命令响应已发送: {}", responseJson);
            } catch (Exception e) {
                logger.error("处理MQTT消息失败", e);
            }
        });
    }

    /**
     * 为所有已存在的设备启动录制
     * 确保设备在线后再启动录制
     */
    private void startRecordingForExistingDevices() {
        try {
            List<DeviceInfo> devices = deviceManager.getAllDevices();
            logger.info("开始为已存在的设备启动录制，设备数量: {}", devices.size());

            for (DeviceInfo device : devices) {
                String deviceId = device.getDeviceId();

                // 确保设备在线（status == 1 且已登录）
                boolean isOnline = (device.getStatus() == 1) &&
                        deviceManager.isDeviceLoggedIn(deviceId);

                if (isOnline) {
                    // 设备在线，启动录制
                    if (recorder.startRecording(deviceId)) {
                        logger.info("已为在线设备启动录制: {}", deviceId);
                    } else {
                        logger.warn("为在线设备启动录制失败: {}", deviceId);
                    }
                } else {
                    // 设备不在线，先尝试登录
                    logger.debug("设备不在线，尝试登录: {} (当前状态: {})", deviceId, device.getStatus());
                    if (deviceManager.loginDevice(device)) {
                        // 登录成功后，再次检查设备状态
                        device = deviceManager.getDevice(deviceId); // 重新获取最新状态
                        if (device.getStatus() == 1 && deviceManager.isDeviceLoggedIn(deviceId)) {
                            if (recorder.startRecording(deviceId)) {
                                logger.info("设备登录成功并启动录制: {}", deviceId);
                            } else {
                                logger.warn("设备登录成功但启动录制失败: {}", deviceId);
                            }
                        } else {
                            logger.warn("设备登录后状态仍为离线，跳过录制启动: {}", deviceId);
                        }
                    } else {
                        logger.debug("设备登录失败，跳过录制启动: {}", deviceId);
                    }
                }
            }

            logger.info("已存在的设备录制启动完成");
        } catch (Exception e) {
            logger.error("为已存在的设备启动录制失败", e);
        }
    }

    /**
     * 设备发现回调
     */
    private void onDeviceFound(DeviceInfo device) {
        try {
            logger.info("发现新设备: {}", device);

            // 尝试自动登录
            if (deviceManager.loginDevice(device)) {
                // 发布设备上线状态 (1: 在线)
                publishDeviceStatus(device, 1);

                // 如果录制功能启用，自动启动录制
                if (recorder != null && config.getRecorder() != null && config.getRecorder().isEnabled()) {
                    recorder.startRecording(device.getDeviceId());
                }
            } else {
                // 发布设备离线状态 (0: 离线)
                publishDeviceStatus(device, 0);
            }
        } catch (Exception e) {
            logger.error("处理设备发现失败", e);
        }
    }

    /**
     * 发布设备状态
     */
    private void publishDeviceStatus(DeviceInfo device, int status) {
        try {
            Map<String, Object> statusMessage = new HashMap<>();
            statusMessage.put("device_id", device.getDeviceId());
            statusMessage.put("status", status);
            statusMessage.put("timestamp", System.currentTimeMillis() / 1000);

            Map<String, Object> deviceInfo = new HashMap<>();
            deviceInfo.put("name", device.getName());
            deviceInfo.put("ip", device.getIp());
            deviceInfo.put("port", device.getPort());
            deviceInfo.put("rtsp_url", device.getRtspUrl());
            statusMessage.put("device_info", deviceInfo);

            String messageJson = objectMapper.writeValueAsString(statusMessage);
            mqttClient.publishStatus(messageJson);
            logger.debug("设备状态已发布: {}", messageJson);
        } catch (Exception e) {
            logger.error("发布设备状态失败", e);
        }
    }

    /**
     * 启动HTTP服务器
     */
    private void startHttpServer() {
        int port = 8084; // 默认端口，可以从配置中读取

        Spark.port(port);

        // 注册WebSocket端点（必须在所有路由映射之前）
        if (radarService != null) {
            try {
                com.digital.video.gateway.api.RadarWebSocketHandler wsHandler = radarService.getWebSocketHandler();
                // 为每个设备注册WebSocket处理器
                com.digital.video.gateway.database.RadarDeviceDAO radarDeviceDAO = new com.digital.video.gateway.database.RadarDeviceDAO(
                        database.getConnection());
                List<com.digital.video.gateway.database.RadarDevice> devices = radarDeviceDAO.getAll();
                for (com.digital.video.gateway.database.RadarDevice device : devices) {
                    com.digital.video.gateway.api.RadarWebSocketEndpoint.registerHandler(
                            device.getDeviceId(), wsHandler);
                }
                // 注册默认处理器（用于动态添加的设备）
                com.digital.video.gateway.api.RadarWebSocketEndpoint.registerHandler("default", wsHandler);

                // 注册WebSocket路由（必须在所有HTTP路由之前）
                // 注意：Spark WebSocket不支持路径参数，使用固定路径+查询参数
                Spark.webSocket("/api/radar/stream",
                        com.digital.video.gateway.api.RadarWebSocketEndpoint.class);
                logger.info("雷达WebSocket端点已注册: /api/radar/stream?deviceId=xxx");
            } catch (Exception e) {
                logger.error("注册WebSocket端点失败", e);
            }
        }

        // 设置CORS（使用after过滤器，避免覆盖控制器设置的Content-Type）
        Spark.after((request, response) -> {
            response.header("Access-Control-Allow-Origin", "*");
            response.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            response.header("Access-Control-Allow-Headers", "Content-Type, Authorization");
        });

        // 为API请求设置默认Content-Type（不包括视频文件）
        Spark.before((request, response) -> {
            String path = request.pathInfo();
            if (path != null && !path.contains("/video") && !path.contains("/snapshot/file")
                    && !path.contains("/export/file")) {
                response.type("application/json");
            }
        });

        // 处理OPTIONS请求
        Spark.options("/*", (request, response) -> {
            return "";
        });

        // 注册认证过滤器
        Spark.before(new AuthFilter());

        // 初始化控制器
        AuthController authController = new AuthController(database);
        HikvisionSDK hikvisionSDKForController = (HikvisionSDK) SDKFactory.getSDK("hikvision");
        DeviceController deviceController = new DeviceController(deviceManager, database, recorder,
                captureService, ptzService, playbackService, hikvisionSDKForController,
                assemblyService, alarmRuleService);
        DriverController driverController = new DriverController(database);
        MqttController mqttController = new MqttController(configService, mqttClient);
        SystemController systemController = new SystemController(configService, mqttClient);
        DashboardController dashboardController = new DashboardController(deviceManager, database, config);

        // 初始化新增控制器（使用已初始化的服务实例）
        AssemblyController assemblyController = new AssemblyController(assemblyService);
        AlarmRuleController alarmRuleController = new AlarmRuleController(alarmRuleService);
        AlarmRecordController alarmRecordController = new AlarmRecordController(alarmRecordService);
        SpeakerController speakerController = new SpeakerController(speakerService);
        RecordingTaskController recordingTaskController = new RecordingTaskController(recordingTaskService);

        // 初始化雷达相关服务
        BackgroundModelService backgroundModelService = new BackgroundModelService(database);
        DefenseZoneService defenseZoneService = new DefenseZoneService(database);
        IntrusionDetectionService intrusionDetectionService = new IntrusionDetectionService(database);
        // radarService可能为null（如果没有雷达设备），需要处理
        radarController = new RadarController(radarTestService, database,
                backgroundModelService, defenseZoneService, intrusionDetectionService, radarService);

        // 注意：WebSocket端点注册在startHttpServer()方法中，因为必须在HTTP路由之前

        // 注册路由
        // 认证路由
        Spark.post("/api/auth/login", authController::login);

        // 设备路由
        Spark.get("/api/devices", deviceController::getDevices);
        Spark.get("/api/devices/brands", deviceController::getBrands);
        Spark.get("/api/devices/:id", deviceController::getDevice);
        Spark.get("/api/devices/:id/config", deviceController::getDeviceConfig);
        Spark.put("/api/devices/:id/config", deviceController::updateDeviceConfig);
        Spark.post("/api/devices", deviceController::addDevice);
        Spark.put("/api/devices/:id", deviceController::updateDevice);
        Spark.delete("/api/devices/:id", deviceController::deleteDevice);
        Spark.post("/api/devices/:id/reboot", deviceController::rebootDevice);
        Spark.post("/api/devices/:id/snapshot", deviceController::captureSnapshot);
        Spark.get("/api/devices/:id/snapshot/file", deviceController::getSnapshotFile);
        Spark.post("/api/devices/:id/ptz", deviceController::ptzControl);
        Spark.get("/api/devices/:id/stream", deviceController::getStreamUrl);
        Spark.get("/api/devices/:id/record-video", deviceController::getRecordVideo);
        Spark.get("/api/devices/:id/video", deviceController::getVideoFile);
        Spark.post("/api/devices/:id/playback", deviceController::playback);
        Spark.get("/api/devices/:id/playback/progress", deviceController::getPlaybackProgress);
        Spark.get("/api/devices/:id/playback/file", deviceController::getPlaybackFile);
        Spark.post("/api/devices/:id/playback/stop", deviceController::stopPlayback);
        Spark.post("/api/devices/:id/export", deviceController::exportVideo);
        Spark.get("/api/devices/:id/export/file", deviceController::getExportFile);

        // 驱动路由
        Spark.get("/api/drivers", driverController::getDrivers);
        Spark.get("/api/drivers/:id", driverController::getDriver);
        Spark.post("/api/drivers", driverController::addDriver);
        Spark.put("/api/drivers/:id", driverController::updateDriver);
        Spark.delete("/api/drivers/:id", driverController::deleteDriver);

        // MQTT路由
        Spark.get("/api/mqtt/config", mqttController::getConfig);
        Spark.put("/api/mqtt/config", mqttController::updateConfig);
        Spark.post("/api/mqtt/test", mqttController::testConnection);

        // 系统配置路由
        Spark.get("/api/system/config", systemController::getConfig);
        Spark.put("/api/system/config", systemController::updateConfig);
        Spark.get("/api/system/health", systemController::healthCheck);
        Spark.post("/api/system/mqtt/restart", systemController::restartMqtt);
        Spark.get("/api/system/logs", systemController::getLogs);

        // 仪表板路由
        Spark.get("/api/dashboard/stats", dashboardController::getStats);
        Spark.get("/api/dashboard/chart", dashboardController::getChart);

        // 装置路由
        Spark.get("/api/assemblies", assemblyController::getAssemblies);
        Spark.get("/api/assemblies/:id", assemblyController::getAssembly);
        Spark.post("/api/assemblies", assemblyController::createAssembly);
        Spark.put("/api/assemblies/:id", assemblyController::updateAssembly);
        Spark.delete("/api/assemblies/:id", assemblyController::deleteAssembly);
        Spark.post("/api/assemblies/:id/devices", assemblyController::addDeviceToAssembly);
        Spark.delete("/api/assemblies/:id/devices/:deviceId", assemblyController::removeDeviceFromAssembly);
        Spark.get("/api/assemblies/:id/devices", assemblyController::getAssemblyDevices);
        Spark.get("/api/devices/:deviceId/assemblies", assemblyController::getAssembliesByDevice);

        // 雷达路由
        Spark.post("/api/radar/test", radarController::testConnection);
        Spark.get("/api/radar/devices", radarController::getRadarDevices);
        Spark.post("/api/radar/devices", radarController::addRadarDevice);
        Spark.post("/api/radar/:deviceId/background/start", radarController::startBackgroundCollection);
        Spark.post("/api/radar/:deviceId/background/stop", radarController::stopBackgroundCollection);
        Spark.get("/api/radar/:deviceId/background/status", radarController::getBackgroundStatus);
        Spark.get("/api/radar/:deviceId/background/collecting/points", radarController::getCollectingPointCloud);
        Spark.get("/api/radar/:deviceId/background/:backgroundId/points", radarController::getBackgroundPoints);
        Spark.get("/api/radar/:deviceId/zones", radarController::getZones);
        Spark.post("/api/radar/:deviceId/zones", radarController::createZone);
        Spark.put("/api/radar/:deviceId/zones/:zoneId", radarController::updateZone);
        Spark.delete("/api/radar/:deviceId/zones/:zoneId", radarController::deleteZone);
        Spark.put("/api/radar/:deviceId/zones/:zoneId/toggle", radarController::toggleZone);
        Spark.get("/api/radar/:deviceId/backgrounds", radarController::getBackgrounds);
        Spark.delete("/api/radar/:deviceId/backgrounds/:backgroundId", radarController::deleteBackground);
        Spark.get("/api/radar/:deviceId/intrusions", radarController::getIntrusions);
        Spark.delete("/api/radar/:deviceId/intrusions", radarController::clearIntrusions);
        Spark.get("/api/radar/intrusions/:id/data", radarController::getIntrusionData);

        // 报警规则路由
        Spark.get("/api/alarm-rules", alarmRuleController::getAlarmRules);
        Spark.get("/api/alarm-rules/:id", alarmRuleController::getAlarmRule);
        Spark.post("/api/alarm-rules", alarmRuleController::createAlarmRule);
        Spark.put("/api/alarm-rules/:id", alarmRuleController::updateAlarmRule);
        Spark.delete("/api/alarm-rules/:id", alarmRuleController::deleteAlarmRule);
        Spark.put("/api/alarm-rules/:id/toggle", alarmRuleController::toggleRule);
        Spark.get("/api/devices/:deviceId/alarm-rules", alarmRuleController::getDeviceRules);
        Spark.get("/api/assemblies/:assemblyId/alarm-rules", alarmRuleController::getAssemblyRules);

        // 报警记录路由
        Spark.get("/api/alarm-records", alarmRecordController::getAlarmRecords);
        Spark.get("/api/alarm-records/:id", alarmRecordController::getAlarmRecord);

        // 音柱路由
        Spark.get("/api/speakers", speakerController::getSpeakers);
        Spark.get("/api/speakers/:deviceId", speakerController::getSpeaker);
        Spark.post("/api/speakers", speakerController::createSpeaker);
        Spark.put("/api/speakers/:deviceId", speakerController::updateSpeaker);
        Spark.delete("/api/speakers/:deviceId", speakerController::deleteSpeaker);
        Spark.post("/api/speakers/:deviceId/play", speakerController::playVoice);

        // 录像任务路由
        Spark.get("/api/recording-tasks", recordingTaskController::getRecordingTasks);
        Spark.get("/api/recording-tasks/:taskId", recordingTaskController::getRecordingTask);
        Spark.post("/api/recording-tasks", recordingTaskController::createRecordingTask);
        Spark.put("/api/recording-tasks/:taskId", recordingTaskController::updateRecordingTask);
        Spark.post("/api/recording-tasks/download", recordingTaskController::downloadRecording);
        Spark.get("/api/recording-tasks/:taskId/file", recordingTaskController::downloadRecordingFile);

        // 异常处理
        Spark.exception(Exception.class, (exception, request, response) -> {
            logger.error("处理请求时发生异常", exception);
            response.status(500);
            response.type("application/json");
            try {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("code", 500);
                errorResponse.put("message", "Internal server error: " + exception.getMessage());
                errorResponse.put("data", null);
                response.body(objectMapper.writeValueAsString(errorResponse));
            } catch (Exception e) {
                response.body("{\"code\":500,\"message\":\"Internal error\",\"data\":null}");
            }
        });

        logger.info("HTTP服务器已启动，端口: {}", port);
    }

    /**
     * 关闭服务
     */
    private void shutdown() {
        logger.info("正在关闭服务...");

        try {
            // 停止保活系统
            if (keeper != null) {
                keeper.stop();
            }

            // 停止设备扫描器
            if (scanner != null) {
                scanner.stop();
            }

            // 停止录制管理器
            if (recorder != null) {
                recorder.stop();
            }

            // 登出所有设备
            if (deviceManager != null) {
                deviceManager.logoutAll();
            }

            // 关闭MQTT客户端
            if (mqttClient != null) {
                mqttClient.close();
            }

            // 关闭数据库
            if (database != null) {
                database.close();
            }

            // 停止HTTP服务器
            Spark.stop();

            // 清理所有SDK
            SDKFactory.cleanup();

            logger.info("服务已关闭");
        } catch (Exception e) {
            logger.error("关闭服务时发生错误", e);
        }
    }
}
