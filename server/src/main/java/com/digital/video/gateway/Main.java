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
import com.digital.video.gateway.mqtt.MqttClientAdapter;
import com.digital.video.gateway.mqtt.MqttPublisher;
import com.digital.video.gateway.mqtt.DelegatingMqttPublisher;
import com.digital.video.gateway.mqtt.FallbackMqttPublisher;
import com.digital.video.gateway.recorder.Recorder;
import com.digital.video.gateway.scanner.DeviceScanner;
import com.digital.video.gateway.service.ConfigService;
import com.digital.video.gateway.service.RecorderService;
import com.digital.video.gateway.service.CaptureService;
import com.digital.video.gateway.service.PTZService;
import com.digital.video.gateway.service.PtzMonitorService;
import com.digital.video.gateway.service.PlaybackService;
import com.digital.video.gateway.service.AlarmService;
import com.digital.video.gateway.service.OssService;
import com.digital.video.gateway.service.AssemblyService;
import com.digital.video.gateway.service.AlarmRuleService;
import com.digital.video.gateway.service.AlarmRecordService;
import com.digital.video.gateway.service.SpeakerService;
import com.digital.video.gateway.service.RecordingTaskService;
import com.digital.video.gateway.service.ZlmMediaService;
import com.digital.video.gateway.database.RadarDeviceDAO;
import com.digital.video.gateway.database.RadarDevice;
import com.digital.video.gateway.database.Assembly;
import com.digital.video.gateway.database.AssemblyDevice;
import com.digital.video.gateway.workflow.FlowContext;
import com.digital.video.gateway.workflow.FlowDefinition;
import com.digital.video.gateway.workflow.FlowExecutor;
import com.digital.video.gateway.workflow.handlers.CaptureHandler;
import com.digital.video.gateway.workflow.handlers.MqttPublishHandler;
import com.digital.video.gateway.workflow.handlers.OssUploadHandler;
import com.digital.video.gateway.workflow.handlers.SpeakerPlayHandler;
import com.digital.video.gateway.workflow.handlers.WebhookHandler;
import com.digital.video.gateway.workflow.handlers.RecordHandler;
import com.digital.video.gateway.workflow.handlers.PTZControlHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.websocket.WsContext;
import com.digital.video.gateway.auth.HaltException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 综合性数字视频监控网关系统主程序
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private Config config;
    private Database database;
    private MqttClient mqttClient;
    private MqttPublisher mqttPublisher;
    private DeviceManager deviceManager;
    private DeviceScanner scanner;
    private CommandHandler commandHandler;
    private Keeper keeper;
    private Recorder recorder;
    private ConfigService configService;
    private RecorderService recorderService;
    private CaptureService captureService;
    private PTZService ptzService;
    private PtzMonitorService ptzMonitorService;
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
    private com.digital.video.gateway.workflow.FlowService flowService;
    private com.digital.video.gateway.api.FlowController flowController;
    private FlowExecutor flowExecutor;
    private AiAnalysisService aiAnalysisService;
    private ScheduledExecutorService statisticsScheduler;
    /** 录像回放下载目录过期清理（3天）定时任务 */
    private ScheduledExecutorService playbackCleanupScheduler;
    private ZlmMediaService zlmMediaService;
    /** MQTT 消息处理线程池，避免命令/工作流在 Paho 回调线程执行导致阻塞 */
    private ExecutorService mqttMessageExecutor;
    private ObjectMapper objectMapper = new ObjectMapper();
    private Javalin app;

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((t, e) ->
                LoggerFactory.getLogger(Main.class).error("未捕获异常 [thread={}]", t.getName(), e));
        Main main = new Main();
        main.start();
    }

    /**
     * 打印启动横幅
     */
    private void printStartupBanner() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                                                                              ║");
        System.out.println("║    ██████╗ ██████╗  ██████╗ ██╗  ██╗     ██████╗ ███████╗███████╗██╗ ██████╗ ║");
        System.out.println("║   ██╔═══██╗██╔══██╗██╔═══██╗██║ ██╔╝    ██╔═══██╗██╔════╝██╔════╝██║██╔═══██╗║");
        System.out.println("║   ██║   ██║██████╔╝██║   ██║█████╔╝     ██║   ██║█████╗  ███████╗██║██║   ██║║");
        System.out.println("║   ██║   ██║██╔══██╗██║   ██║██╔═██╗     ██║   ██║██╔══╝  ╚════██║██║██║   ██║║");
        System.out.println("║   ╚██████╔╝██████╔╝╚██████╔╝██║  ██╗    ╚██████╔╝███████╗███████║██║╚██████╔╝║");
        System.out.println("║    ╚═════╝ ╚═════╝  ╚═════╝ ╚═╝  ╚═╝     ╚═════╝ ╚══════╝╚══════╝╚═╝ ╚═════╝ ║");
        System.out.println("║                                                                              ║");
        System.out.println("║                   综合性数字视频监控网关预警系统                                  ║");
        System.out.println("║                   Digital Video Gateway Warning System                       ║");
        System.out.println("║                                                                              ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════════════╣");
        System.out.println("║                                                                              ║");
        System.out.println("║                         HOOK DESIGN                                          ║");
        System.out.println("║                                                                              ║");
        System.out.println("║                          作者: Hook                                           ║");
        System.out.println("║                           邮箱: 1959595510@qq.com                             ║");
        System.out.println("║                                                                              ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    /**
     * 启动服务
     */
    public void start() {
        // 打印启动横幅
        printStartupBanner();

        logger.info("启动综合性数字视频监控网关预警系统...");

        try {
            // 1. 加载配置（优先使用外部 ./config/config.yaml，便于部署时修改 zlm 等配置）
            java.io.File externalConfig = new java.io.File("./config/config.yaml");
            if (externalConfig.exists()) {
                config = ConfigLoader.loadFromFile(externalConfig.getAbsolutePath());
            } else {
                config = ConfigLoader.load("config.yaml");
            }
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

            // 4.5. 创建 ZLM 服务实例（暂不启动，等雷达 SDK 初始化完成后再 start，
            //       避免两个原生库的 epoll/IO 线程初始化顺序冲突导致 Livox SDK SIGSEGV）
            zlmMediaService = new ZlmMediaService(config.getZlm());

            // 5. 初始化功能服务类
            recorderService = new RecorderService(deviceManager, config.getRecorder(), zlmMediaService);
            captureService = new CaptureService(deviceManager, "./storage/captures");
            ptzService = new PTZService(deviceManager);
            ptzMonitorService = new PtzMonitorService(database, deviceManager, config);
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
            recordingTaskService.setConfigService(configService); // 注入ConfigService用于Webhook通知
            flowService = new com.digital.video.gateway.workflow.FlowService(database);
            // 确保默认报警流程存在
            flowService.ensureDefaultAlarmFlow();
            flowService.ensureDefaultRadarIntrusionFlow();

            // 雷达服务初始化（必须在 ZLM start 之前，两个原生库的 epoll 初始化顺序敏感）
            try {
                radarService = new RadarService(ptzService, database);
                if (captureService != null) {
                    radarService.setCaptureService(captureService);
                }
                if (assemblyService != null) {
                    radarService.setAssemblyService(assemblyService);
                }
                // 注意：PointCloudProcessCenter 需要 flowExecutor，但此时 flowExecutor 尚未创建。
                // 推迟到 flowExecutor 初始化之后再注入（见下方 initPointCloudProcessCenter 调用）。
                if (config.getLog() != null) {
                    radarService.setStatsLogIntervalSeconds(config.getLog().getPointcloudLogInterval());
                }
                radarService.start();
                RadarDeviceDAO radarDeviceDAO = new RadarDeviceDAO(database.getConnection());
                List<com.digital.video.gateway.database.RadarDevice> radarDevices =
                        radarDeviceDAO.getAll();
                logger.info("雷达服务已启动，当前配置了 {} 个雷达设备", radarDevices.size());

                radarTestService = new RadarTestService(radarService);
                logger.info("新增服务初始化成功（RadarTestService 已关联 RadarService）");

            } catch (Throwable e) {
                logger.error("雷达服务启动失败，将继续启动其他服务", e);
                logger.warn("雷达服务不可用，可能是Livox SDK依赖问题，但不影响其他功能");
                radarService = null;
                radarTestService = new RadarTestService();
                logger.info("RadarTestService 已初始化（降级模式，仅支持 UDP 探测）");
            }

            // 5.7.5. 雷达 SDK 初始化完成后，再启动 ZLM 内嵌服务
            //        （两个原生库的加载顺序：Livox SDK → ZLM mk_api，避免 epoll 冲突）
            zlmMediaService.start();
            if (zlmMediaService.isStarted()) {
                logger.info("ZLM 内嵌服务已就绪（供海康循环录像使用）");
            }

            // 5.8. 启动PTZ监控服务
            ptzMonitorService.start();
            logger.info("PTZ监控服务{}启动", ptzMonitorService.isRunning() ? "已" : "未");

            // 5.9. 注入依赖到AlarmService
            alarmService.setAlarmRuleService(alarmRuleService);
            alarmService.setAlarmRecordService(alarmRecordService);
            alarmService.setRecordingTaskService(recordingTaskService);
            alarmService.setSpeakerService(speakerService);
            alarmService.setPTZService(ptzService);
            alarmService.setDatabase(database);
            logger.info("AlarmService依赖注入完成");

            logger.info("功能服务类初始化成功");

            // 6. 初始化录制管理器（使用RecorderService）
            recorder = new Recorder(recorderService, config.getRecorder());
            recorder.start();
            logger.info("录制管理器初始化成功");

            // 6.5. 初始化 MQTT 消息处理线程池（有界队列，避免回调线程被长时间占用）
            mqttMessageExecutor = new ThreadPoolExecutor(4, 8, 60L, TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(256),
                    r -> {
                        Thread t = new Thread(r, "MqttMessageHandler");
                        t.setDaemon(true);
                        return t;
                    },
                    (r, e) -> logger.warn("MQTT 消息处理队列已满，丢弃本条消息"));

            // 7. 初始化MQTT客户端（MQTT连接失败不应阻止服务启动）
            mqttClient = new MqttClient(config.getMqtt());
            setupMqttMessageHandler();
            if (!mqttClient.connect()) {
                logger.warn("MQTT连接失败，服务将继续运行但MQTT功能不可用（可以稍后通过API重启MQTT连接）");
                // 不退出服务，允许HTTP服务器正常启动
            } else {
                logger.info("MQTT客户端连接成功");
            }
            mqttPublisher = new DelegatingMqttPublisher(new MqttClientAdapter(mqttClient), new FallbackMqttPublisher());

            // 配置报警服务的MQTT与流程执行器
            alarmService.setMqttPublisher(mqttPublisher);
            flowExecutor = createFlowExecutor();
            alarmService.setFlowService(flowService);
            alarmService.setFlowExecutor(flowExecutor);
            setupMqttTopicHandler();

            // 7.4.1 初始化点云处理中心（必须在 flowExecutor 创建之后）
            if (radarService != null && flowService != null && flowExecutor != null) {
                PointCloudProcessCenter processCenter = new PointCloudProcessCenter(
                        radarService.getTargetTrackingService(),
                        radarService.getMotionPredictionService(),
                        ptzService, flowService, flowExecutor,
                        assemblyService, database.getConnection());
                radarService.setPointCloudProcessCenter(processCenter);
                logger.info("已初始化点云处理中心（雷达入侵工作流）");
            }

            // 7.5. 设置DeviceManager的MQTT发布器（连接失败时自动降级）
            deviceManager.setMqttPublisher(mqttPublisher);
            logger.info("DeviceManager已设置MQTT发布器");

            // 7.6. 启动时自动连接已有设备，确保在线状态
            autoConnectExistingDevices();

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

            // 7.8. 设置天地伟业SDK报警回调
            com.digital.video.gateway.tiandy.TiandySDK tiandySDK = (com.digital.video.gateway.tiandy.TiandySDK) SDKFactory
                    .getSDK("tiandy");
            if (tiandySDK != null) {
                tiandySDK.setDeviceManager(deviceManager); // 先设置设备管理器，用于查找设备ID
                tiandySDK.setAlarmService(alarmService);
                logger.info("天地伟业SDK报警回调已设置");
            }

            // 8. 初始化命令处理器（使用功能服务类）
            commandHandler = new CommandHandler(deviceManager, hikvisionSDK, recorder,
                    captureService, ptzService, playbackService);
            commandHandler.setRecorderService(recorderService);
            commandHandler.setOssService(ossService);
            commandHandler.setRecordingTaskService(recordingTaskService);
            logger.info("命令处理器初始化成功");

            // 9. 启动设备扫描器（暂时使用海康SDK，后续可扩展为多品牌）
            scanner = new DeviceScanner(hikvisionSDK, database, deviceManager, config.getScanner(), config.getDevice());
            scanner.setDeviceFoundCallback(this::onDeviceFound);
            if (scanner.start()) {
                logger.info("设备扫描器启动成功");
            } else {
                logger.info("设备扫描器未启动（可能已禁用）");
            }

            // 10. 启动保活系统
            keeper = new Keeper(deviceManager, config.getKeeper(), recorder);
            keeper.start();
            logger.info("保活系统启动成功");

            // 10.5. 启动设备状态统计任务（每小时记录一次设备状态快照）
            startDeviceStatusStatisticsTask();
            logger.info("设备状态统计任务已启动");

            // 10.6. 启动录像回放下载目录过期清理（每日凌晨2点，保留近3天）
            startPlaybackDownloadCleanupTask();
            logger.info("录像回放下载清理任务已启动（每日02:00，保留3天）");

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
            logger.info("综合性数字视频监控网关预警系统启动成功！");
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
     * 设置 MQTT 消息处理器（仅命令主题，工作流主题在 setupMqttTopicHandler 中按 topic 派发）
     */
    private void setupMqttMessageHandler() {
        // 占位，实际派发在 setupMqttTopicHandler 中按 (topic, payload) 处理
    }

    /**
     * 设置按主题派发的 MQTT 处理器，并订阅工作流 mqtt_subscribe 节点配置的主题
     */
    private void setupMqttTopicHandler() {
        if (mqttClient == null || flowExecutor == null || flowService == null || mqttMessageExecutor == null)
            return;
        final String commandTopic = config.getMqtt().getCommandTopic();
        mqttClient.setTopicMessageHandler((topic, payload) -> {
            // 投递到独立线程池执行，避免阻塞 Paho 回调线程
            mqttMessageExecutor.execute(() -> {
                try {
                    if (commandTopic.equals(topic)) {
                        logger.info("收到MQTT命令: {}", payload);
                        CommandResponse response = commandHandler.handleCommand(payload);
                        String responseJson = objectMapper.writeValueAsString(response);
                        if (mqttPublisher != null)
                            mqttPublisher.publishResponse(responseJson);
                        else if (mqttClient != null)
                            mqttClient.publishResponse(responseJson);
                        logger.info("命令响应已发送: {}", responseJson);
                    } else {
                        for (FlowDefinition def : flowService.getFlowDefinitionsByMqttTopic(topic)) {
                            try {
                                FlowContext ctx = new FlowContext();
                                @SuppressWarnings("unchecked")
                                Map<String, Object> payloadMap = payload != null && !payload.isEmpty()
                                        ? objectMapper.readValue(payload, Map.class)
                                        : new HashMap<>();
                                ctx.setPayload(payloadMap);
                                flowExecutor.execute(def, ctx);
                            } catch (Exception e) {
                                logger.error("MQTT 工作流执行失败 topic={} flowId={}", topic, def.getFlowId(), e);
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.error("处理MQTT消息失败 topic={}", topic, e);
                }
            });
        });
        subscribeMqttWorkflowTopics();
        mqttClient.setOnConnectedCallback(this::subscribeMqttWorkflowTopics);
    }

    /**
     * 订阅工作流 mqtt_subscribe 节点配置的主题（连接/重连后调用）
     */
    private void subscribeMqttWorkflowTopics() {
        if (mqttClient == null || flowService == null)
            return;
        for (String topic : flowService.getMqttSubscribeTopics()) {
            mqttClient.subscribe(topic);
            logger.info("已订阅工作流主题: {}", topic);
        }
    }

    /**
     * 初始化流程执行器并注册节点处理器
     */
    private FlowExecutor createFlowExecutor() {
        FlowExecutor executor = new FlowExecutor();
        // 事件触发器：支持防抖间隔配置
        executor.registerHandler("event_trigger",
                new com.digital.video.gateway.workflow.handlers.EventTriggerHandler());
        executor.registerHandler("capture", new CaptureHandler(captureService));
        executor.registerHandler("mqtt_publish",
                new MqttPublishHandler(mqttPublisher != null ? mqttPublisher : new FallbackMqttPublisher()));
        executor.registerHandler("mqtt_subscribe",
                new com.digital.video.gateway.workflow.handlers.MqttSubscribeHandler());

        // OssService 设置 ConfigService 以支持动态配置
        if (ossService != null) {
            ossService.setConfigService(configService);
        }
        executor.registerHandler("oss_upload", new OssUploadHandler(ossService));
        executor.registerHandler("speaker_play", new SpeakerPlayHandler(speakerService));

        // WebhookHandler 传入 ConfigService，从数据库读取全局通知配置
        executor.registerHandler("webhook", new WebhookHandler(configService));
        RecordHandler recordHandler = new RecordHandler(recordingTaskService, ossService);
        recordHandler.setRecorderService(recorderService);
        recordHandler.setDeviceManager(deviceManager);
        executor.registerHandler("record", recordHandler);
        executor.registerHandler("ptz_control", new PTZControlHandler(ptzService));
        executor.registerHandler("delay", new com.digital.video.gateway.workflow.handlers.DelayHandler());
        executor.registerHandler("condition", new com.digital.video.gateway.workflow.handlers.ConditionHandler());
        executor.registerHandler("http_request", new com.digital.video.gateway.workflow.handlers.HttpRequestHandler());
        executor.registerHandler("ptz_goto",
                new com.digital.video.gateway.workflow.handlers.PtzGotoHandler(ptzService));
        executor.registerHandler("device_reboot",
                new com.digital.video.gateway.workflow.handlers.DeviceRebootHandler(deviceManager));
        executor.registerHandler("radar_zone_toggle",
                new com.digital.video.gateway.workflow.handlers.RadarZoneToggleHandler(database, radarService));
        executor.registerHandler("ai_inference", new com.digital.video.gateway.workflow.handlers.AiInferenceHandler());
        com.digital.video.gateway.service.AiGatewayClient aiClient = new com.digital.video.gateway.service.AiGatewayClient(configService);
        aiAnalysisService = new AiAnalysisService(database);
        executor.registerHandler("ai_verify", new com.digital.video.gateway.workflow.handlers.AiVerifyHandler(aiClient, aiAnalysisService, database));
        executor.registerHandler("ai_alert_text", new com.digital.video.gateway.workflow.handlers.AiAlertTextHandler(aiClient, aiAnalysisService));
        executor.registerHandler("ai_tts", new com.digital.video.gateway.workflow.handlers.AiTtsHandler(configService, aiAnalysisService));
        executor.registerHandler("system_speaker", new com.digital.video.gateway.workflow.handlers.SystemSpeakerHandler());
        executor.registerHandler("radar_intrusion",
                new com.digital.video.gateway.workflow.handlers.RadarIntrusionHandler(ptzService, captureService));
        return executor;
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
     * 启动时自动连接数据库中已存在的设备
     */
    private void autoConnectExistingDevices() {
        try {
            List<DeviceInfo> devices = deviceManager.getAllDevices();
            logger.info("启动自动连接已有设备，数量: {}", devices.size());

            for (DeviceInfo device : devices) {
                String deviceId = device.getDeviceId();

                // 已在线且已登录则跳过
                if (deviceManager.isDeviceLoggedIn(deviceId)) {
                    logger.debug("设备已登录，跳过自动连接: {}", deviceId);
                    continue;
                }

                boolean success = deviceManager.loginDevice(device);
                if (success) {
                    publishDeviceStatus(device, 1);
                    logger.info("自动登录成功: {}", deviceId);
                } else {
                    publishDeviceStatus(device, 0);
                    logger.warn("自动登录失败: {}", deviceId);
                }
            }
            // 发布所有雷达状态到 senhub/device/status（entity_type=radar）
            RadarDeviceDAO radarDeviceDAO = new RadarDeviceDAO(database.getConnection());
            List<RadarDevice> radarDevices = radarDeviceDAO.getAll();
            for (RadarDevice rd : radarDevices) {
                publishRadarStatus(rd, rd.getStatus());
            }
            // 发布所有装置状态到 senhub/assembly/{assemblyId}/status（含经纬度、device_ids）
            List<Assembly> assemblies = assemblyService.getAssemblies(null, null);
            for (Assembly a : assemblies) {
                publishAssemblyStatus(a, a.getStatus() == 1 ? "online" : "offline");
            }
        } catch (Exception e) {
            logger.error("自动连接已有设备失败", e);
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
     * 发布设备状态到 senhub/device/status（entity_type=camera、device_id、type、device_info 含
     * camera_type）
     */
    private void publishDeviceStatus(DeviceInfo device, int status) {
        try {
            Map<String, Object> statusMessage = new HashMap<>();
            statusMessage.put("entity_type", "camera");
            statusMessage.put("device_id", device.getDeviceId());
            statusMessage.put("type", status == 1 ? "online" : "offline");
            statusMessage.put("timestamp", System.currentTimeMillis() / 1000);

            Map<String, Object> deviceInfo = new HashMap<>();
            deviceInfo.put("name", device.getName());
            deviceInfo.put("ip", device.getIp());
            deviceInfo.put("port", device.getPort());
            deviceInfo.put("rtsp_url", device.getRtspUrl());
            deviceInfo.put("brand", device.getBrand());
            deviceInfo.put("camera_type", device.getCameraType() != null ? device.getCameraType() : "other");
            if (device.getSerialNumber() != null && !device.getSerialNumber().isEmpty()) {
                deviceInfo.put("serial_number", device.getSerialNumber());
            }
            statusMessage.put("device_info", deviceInfo);
            if (status != 1)
                statusMessage.put("reason", "disconnected");

            String messageJson = objectMapper.writeValueAsString(statusMessage);
            if (mqttPublisher != null)
                mqttPublisher.publishStatus(messageJson);
            logger.debug("设备状态已发布: {}", messageJson);
        } catch (Exception e) {
            logger.error("发布设备状态失败", e);
        }
    }

    /**
     * 发布雷达状态到 senhub/device/status（entity_type=radar、device_id、radar_info）
     */
    private void publishRadarStatus(RadarDevice device, int status) {
        if (mqttPublisher == null)
            return;
        try {
            Map<String, Object> msg = new HashMap<>();
            msg.put("entity_type", "radar");
            msg.put("device_id", device.getDeviceId());
            msg.put("type", status == 1 ? "online" : "offline");
            msg.put("timestamp", System.currentTimeMillis() / 1000);
            Map<String, Object> radarInfo = new HashMap<>();
            radarInfo.put("radar_ip", device.getRadarIp());
            radarInfo.put("radar_name", device.getRadarName());
            radarInfo.put("assembly_id", device.getAssemblyId());
            radarInfo.put("radar_serial", device.getRadarSerial());
            radarInfo.put("status", status);
            msg.put("radar_info", radarInfo);
            if (status != 1)
                msg.put("reason", "disconnected");
            String json = objectMapper.writeValueAsString(msg);
            mqttPublisher.publish(config.getMqtt().getStatusTopic(), json);
            logger.debug("雷达状态已发布: {}", device.getDeviceId());
        } catch (Exception e) {
            logger.error("发布雷达状态失败: {}", device.getDeviceId(), e);
        }
    }

    /**
     * 发布装置状态到 senhub/assembly/{assemblyId}/status（含 longitude、latitude、device_ids）
     */
    private void publishAssemblyStatus(Assembly assembly, String type) {
        if (mqttPublisher == null)
            return;
        try {
            String topic = "senhub/assembly/" + assembly.getAssemblyId() + "/status";
            Map<String, Object> msg = new HashMap<>();
            msg.put("assembly_id", assembly.getAssemblyId());
            msg.put("type", type);
            msg.put("timestamp", System.currentTimeMillis() / 1000);
            Map<String, Object> info = new HashMap<>();
            info.put("name", assembly.getName());
            info.put("location", assembly.getLocation());
            if (assembly.getLongitude() != null)
                info.put("longitude", assembly.getLongitude());
            if (assembly.getLatitude() != null)
                info.put("latitude", assembly.getLatitude());
            List<AssemblyDevice> devices = assemblyService.getAssemblyDevices(assembly.getAssemblyId());
            List<String> deviceIds = devices.stream().map(AssemblyDevice::getDeviceId).collect(Collectors.toList());
            info.put("device_count", deviceIds.size());
            info.put("device_ids", deviceIds);
            msg.put("assembly_info", info);
            String json = objectMapper.writeValueAsString(msg);
            mqttPublisher.publish(topic, json);
            logger.debug("装置状态已发布: {}", assembly.getAssemblyId());
        } catch (Exception e) {
            logger.error("发布装置状态失败: {}", assembly.getAssemblyId(), e);
        }
    }

    /**
     * 启动设备状态统计任务
     * 每小时记录一次所有设备的当前状态，用于生成连接趋势图表
     */
    private void startDeviceStatusStatisticsTask() {
        statisticsScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "DeviceStatusStatistics");
            t.setDaemon(true);
            return t;
        });

        // 计算到下一个整点的延迟时间（例如：如果现在是14:30，则延迟30分钟）
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int currentMinute = cal.get(java.util.Calendar.MINUTE);
        int currentSecond = cal.get(java.util.Calendar.SECOND);
        long delayToNextHour = (60 - currentMinute) * 60 - currentSecond;

        // 立即执行一次，然后每小时执行一次
        statisticsScheduler.schedule(() -> {
            recordDeviceStatusSnapshot();
        }, delayToNextHour, TimeUnit.SECONDS);

        statisticsScheduler.scheduleAtFixedRate(
                this::recordDeviceStatusSnapshot,
                delayToNextHour + 3600, // 第一次执行后，再等1小时
                3600, // 每小时执行一次
                TimeUnit.SECONDS);

        logger.info("设备状态统计任务已启动，将在{}秒后首次执行，之后每小时执行一次", delayToNextHour);
    }

    /**
     * 记录设备状态快照
     * 记录所有设备的当前状态到device_history表，用于生成连接趋势图表
     */
    private void recordDeviceStatusSnapshot() {
        try {
            List<DeviceInfo> devices = deviceManager.getAllDevices();
            int recordedCount = 0;

            for (DeviceInfo device : devices) {
                // 记录当前设备状态（即使状态未变化，也要记录用于统计）
                database.recordDeviceHistory(device.getDeviceId(), device.getStatus());
                recordedCount++;
            }

            logger.debug("设备状态快照已记录: {} 个设备", recordedCount);
        } catch (Exception e) {
            logger.error("记录设备状态快照失败", e);
        }
    }

    /**
     * 启动录像回放下载目录过期清理任务：每日凌晨2点执行，删除超过3天的 .mp4 文件
     */
    private void startPlaybackDownloadCleanupTask() {
        playbackCleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PlaybackDownloadCleanup");
            t.setDaemon(true);
            return t;
        });
        java.util.Calendar cal = java.util.Calendar.getInstance();
        long now = cal.getTimeInMillis();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 2);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= now) {
            cal.add(java.util.Calendar.DAY_OF_MONTH, 1);
        }
        long delayMs = cal.getTimeInMillis() - now;
        long periodMs = 24 * 60 * 60 * 1000L;
        playbackCleanupScheduler.scheduleAtFixedRate(
                this::cleanupExpiredPlaybackDownloads,
                delayMs, periodMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 清理 storage/downloads 下超过3天的录像片段
     */
    private void cleanupExpiredPlaybackDownloads() {
        try {
            java.io.File downloadsRoot = new java.io.File("./storage/downloads");
            if (!downloadsRoot.exists() || !downloadsRoot.isDirectory()) return;
            long cutoff = System.currentTimeMillis() - 3L * 24 * 60 * 60 * 1000;
            int deletedCount = 0;
            java.io.File[] deviceDirs = downloadsRoot.listFiles(java.io.File::isDirectory);
            if (deviceDirs == null) return;
            for (java.io.File deviceDir : deviceDirs) {
                java.io.File[] files = deviceDir.listFiles((d, name) -> name != null && name.endsWith(".mp4"));
                if (files == null) continue;
                for (java.io.File f : files) {
                    if (f.lastModified() < cutoff) {
                        if (f.delete()) deletedCount++;
                        else logger.warn("删除过期录像文件失败: {}", f.getAbsolutePath());
                    }
                }
                if (deviceDir.list() != null && deviceDir.list().length == 0) {
                    if (deviceDir.delete()) logger.debug("已删除空目录: {}", deviceDir.getAbsolutePath());
                }
            }
            if (deletedCount > 0) logger.info("录像回放清理: 已删除 {} 个超过3天的录像文件", deletedCount);
        } catch (Exception e) {
            logger.error("录像回放清理任务执行失败", e);
        }
    }

    /**
     * 启动HTTP服务器
     */
    private void startHttpServer() {
        int port = 8084;

        app = Javalin.create(config -> {
            config.plugins.enableCors(cors -> cors.add(it -> it.anyHost()));
            config.staticFiles.add(sf -> {
                sf.hostedPath = "/";
                sf.directory = "/static";
                sf.location = Location.CLASSPATH;
            });
            config.spaRoot.addFile("/", "/static/index.html", Location.CLASSPATH);
        });

        // WebSocket 处理器映射（deviceId -> handler）
        final java.util.Map<String, com.digital.video.gateway.api.RadarWebSocketHandler> radarWsHandlers = new java.util.concurrent.ConcurrentHashMap<>();
        final java.util.Map<org.eclipse.jetty.websocket.api.Session, String> sessionToDeviceId = new java.util.concurrent.ConcurrentHashMap<>();
        try {
            if (radarService != null) {
                com.digital.video.gateway.api.RadarWebSocketHandler wsHandler = radarService.getWebSocketHandler();
                com.digital.video.gateway.database.RadarDeviceDAO radarDeviceDAO = new com.digital.video.gateway.database.RadarDeviceDAO(database.getConnection());
                List<com.digital.video.gateway.database.RadarDevice> devices = radarDeviceDAO.getAll();
                for (com.digital.video.gateway.database.RadarDevice device : devices) {
                    radarWsHandlers.put(device.getDeviceId(), wsHandler);
                }
                radarWsHandlers.put("default", wsHandler);
                logger.info("雷达WebSocket已配置（正常）: /ws/radar/stream?deviceId=xxx");
            } else {
                radarWsHandlers.put("default", new com.digital.video.gateway.api.RadarWebSocketHandlerFallback());
                logger.info("雷达WebSocket已配置（降级）: /ws/radar/stream?deviceId=xxx");
            }
        } catch (Exception e) {
            logger.error("配置雷达WebSocket处理器失败", e);
        }

        final com.fasterxml.jackson.databind.ObjectMapper wsObjectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        app.ws("/ws/radar/stream", ws -> {
            ws.onConnect(ctx -> {
                try {
                    String deviceId = ctx.queryParam("deviceId");
                    if (deviceId == null || deviceId.trim().isEmpty()) {
                        logger.warn("WebSocket连接缺少deviceId查询参数");
                        ctx.closeSession(1008, "Invalid request: missing deviceId parameter");
                        return;
                    }
                    com.digital.video.gateway.api.RadarWebSocketHandler handler = radarWsHandlers.get(deviceId);
                    if (handler == null) handler = radarWsHandlers.get("default");
                    if (handler == null) {
                        logger.warn("未找到deviceId对应的处理器: {}", deviceId);
                        ctx.closeSession(1008, "Device handler not found");
                        return;
                    }
                    sessionToDeviceId.put(ctx.session, deviceId);
                    handler.addConnection(deviceId, ctx.session);
                    Map<String, Object> welcome = new HashMap<>();
                    welcome.put("type", "status");
                    welcome.put("connected", true);
                    welcome.put("deviceId", deviceId);
                    welcome.put("message", "WebSocket连接成功");
                    ctx.send(wsObjectMapper.writeValueAsString(welcome));
                } catch (Exception e) {
                    logger.error("WebSocket连接处理失败", e);
                    ctx.closeSession(1011, "Internal error");
                }
            });
            ws.onMessage(ctx -> { /* 订阅等已在 onConnect 处理 */ });
            ws.onClose(ctx -> {
                String deviceId = sessionToDeviceId.remove(ctx.session);
                logger.info("WebSocket连接关闭: deviceId={}, statusCode={}, reason={}", deviceId, ctx.status(), ctx.reason());
                for (com.digital.video.gateway.api.RadarWebSocketHandler h : radarWsHandlers.values()) {
                    h.removeConnection(ctx.session);
                }
            });
        });

        // 为所有返回 JSON 的 /api 请求统一设置 Content-Type（文件/视频接口由控制器自行覆盖）
        app.before("/api/*", ctx -> {
            String path = ctx.path();
            if (path == null) return;
            if (path.contains("/snapshot/file") || path.contains("/playback/file") || path.contains("/export/file")
                    || path.contains("/video") || (path.contains("recording-tasks") && path.contains("/file")))
                return;
            ctx.contentType("application/json; charset=utf-8");
        });
        app.before("/api/*", AuthFilter::handle);

        // 初始化控制器
        AuthController authController = new AuthController(database);
        HikvisionSDK hikvisionSDKForController = (HikvisionSDK) SDKFactory.getSDK("hikvision");
        ZlmProxyService zlmProxyService = null;
        if (zlmMediaService != null && zlmMediaService.isStarted() && zlmMediaService.getConfig() != null) {
            com.digital.video.gateway.service.ZlmApiClient zlmApiClient = new com.digital.video.gateway.service.ZlmApiClient(
                    "http://127.0.0.1:" + zlmMediaService.getConfig().getHttpPort(),
                    zlmMediaService.getConfig().getApiSecret());
            zlmProxyService = new com.digital.video.gateway.service.ZlmProxyService(
                    zlmMediaService.getApi(), zlmApiClient, deviceManager,
                    zlmMediaService.getConfig().getHttpPort(), zlmMediaService.getConfig().getRtmpPort());
        }
        DeviceController deviceController = new DeviceController(deviceManager, database, recorder,
                captureService, ptzService, ptzMonitorService, playbackService, hikvisionSDKForController,
                assemblyService, alarmRuleService, zlmProxyService);
        DriverController driverController = new DriverController(database);
        MqttController mqttController = new MqttController(configService, mqttClient);
        SystemController systemController = new SystemController(configService, mqttClient);
        if (aiAnalysisService != null) {
            systemController.setAiAnalysisService(aiAnalysisService);
        }
        DashboardController dashboardController = new DashboardController(deviceManager, database, config);
        NotificationController notificationController = new NotificationController(database);

        // 初始化新增控制器（使用已初始化的服务实例）
        AssemblyController assemblyController = new AssemblyController(assemblyService);
        AlarmRuleController alarmRuleController = new AlarmRuleController(alarmRuleService);
        AlarmRecordController alarmRecordController = new AlarmRecordController(alarmRecordService, deviceManager, assemblyService);
        SpeakerController speakerController = new SpeakerController(speakerService);
        RecordingTaskController recordingTaskController = new RecordingTaskController(recordingTaskService);
        flowController = new FlowController(flowService, flowExecutor);

        // 初始化雷达相关服务
        BackgroundModelService backgroundModelService = new BackgroundModelService(database);
        DefenseZoneService defenseZoneService = new DefenseZoneService(database);
        IntrusionDetectionService intrusionDetectionService = new IntrusionDetectionService(database);
        // radarService可能为null（如果没有雷达设备），需要处理
        radarController = new RadarController(radarTestService, database,
                backgroundModelService, defenseZoneService, intrusionDetectionService, radarService, assemblyService);

        // 初始化扫描控制器
        ScannerController scannerController = new ScannerController(scanner, deviceManager, database);

        // 注意：WebSocket端点注册在startHttpServer()方法中，因为必须在HTTP路由之前

        // 注册路由
        app.post("/api/auth/login", authController::login);

        app.get("/api/devices", deviceController::getDevices);
        app.get("/api/devices/brands", deviceController::getBrands);
        app.get("/api/devices/suggest-gb-id", deviceController::suggestGbId);
        app.put("/api/devices/{id}/set-gb-id", deviceController::setDeviceGbId);
        app.get("/api/devices/{id}", deviceController::getDevice);
        app.get("/api/devices/{id}/config", deviceController::getDeviceConfig);
        app.put("/api/devices/{id}/config", deviceController::updateDeviceConfig);
        app.post("/api/devices", deviceController::addDevice);
        app.put("/api/devices/{id}", deviceController::updateDevice);
        app.delete("/api/devices/{id}", deviceController::deleteDevice);
        app.post("/api/devices/{id}/reboot", deviceController::rebootDevice);
        app.post("/api/devices/{id}/snapshot", deviceController::captureSnapshot);
        app.get("/api/devices/{id}/snapshot/file", deviceController::getSnapshotFile);
        app.post("/api/devices/{id}/ptz", deviceController::ptzControl);
        app.post("/api/devices/{id}/ptz/goto", deviceController::ptzGoto);
        app.get("/api/devices/{id}/ptz/position", deviceController::getPtzPosition);
        app.post("/api/devices/{id}/ptz/refresh", deviceController::refreshPtzPosition);
        app.put("/api/devices/{id}/ptz/monitor", deviceController::setPtzMonitor);
        app.get("/api/devices/{id}/stream", deviceController::getStreamUrl);
        app.get("/api/devices/{id}/live/url", deviceController::getLiveUrl);
        app.get("/api/devices/{id}/record-video", deviceController::getRecordVideo);
        app.get("/api/devices/{id}/video", deviceController::getVideoFile);
        app.post("/api/devices/{id}/playback", deviceController::playback);
        app.get("/api/devices/{id}/playback/progress", deviceController::getPlaybackProgress);
        app.get("/api/devices/{id}/playback/file", deviceController::getPlaybackFile);
        app.get("/api/devices/{id}/playback/transcode-url", deviceController::getPlaybackTranscodeUrl);
        app.post("/api/devices/{id}/playback/transcode-stop", deviceController::postPlaybackTranscodeStop);
        app.post("/api/devices/{id}/playback/stop", deviceController::stopPlayback);
        app.post("/api/devices/{id}/export", deviceController::exportVideo);
        app.get("/api/devices/{id}/export/file", deviceController::getExportFile);

        app.get("/api/drivers", driverController::getDrivers);
        app.get("/api/drivers/check-all", driverController::checkAllDrivers);
        app.get("/api/drivers/logs", driverController::getDriverLogs);
        app.get("/api/drivers/{id}/check", driverController::checkDriver);
        app.get("/api/drivers/{id}", driverController::getDriver);
        app.post("/api/drivers", driverController::addDriver);
        app.put("/api/drivers/{id}", driverController::updateDriver);
        app.delete("/api/drivers/{id}", driverController::deleteDriver);

        app.get("/api/mqtt/config", mqttController::getConfig);
        app.put("/api/mqtt/config", mqttController::updateConfig);
        app.post("/api/mqtt/test", mqttController::testConnection);

        app.get("/api/system/config", systemController::getConfig);
        app.put("/api/system/config", systemController::updateConfig);
        app.get("/api/system/health", systemController::healthCheck);

        app.post("/api/scanner/start", scannerController::startScan);
        app.get("/api/scanner/status/{sessionId}", scannerController::getScanStatus);
        app.post("/api/scanner/add-devices", scannerController::addDevices);
        app.post("/api/system/mqtt/restart", systemController::restartMqtt);
        app.get("/api/system/logs", systemController::getLogs);
        app.post("/api/system/notification/test", systemController::testNotification);
        app.post("/api/system/ai/test", systemController::testAiConnection);
        app.get("/api/system/ai-analysis-records", systemController::getAiAnalysisRecords);
        app.post("/api/system/ai-analysis-records/batch-delete", systemController::batchDeleteAiAnalysisRecords);
        app.delete("/api/system/ai-analysis-records/{id}", systemController::deleteAiAnalysisRecord);

        app.get("/api/dashboard/stats", dashboardController::getStats);
        app.get("/api/dashboard/chart", dashboardController::getChart);

        app.get("/api/notifications", notificationController::getNotifications);
        app.post("/api/notifications/read", notificationController::markAsRead);
        app.post("/api/notifications/read-all", notificationController::markAllAsRead);

        app.get("/api/assemblies", assemblyController::getAssemblies);
        app.get("/api/assemblies/{id}", assemblyController::getAssembly);
        app.post("/api/assemblies", assemblyController::createAssembly);
        app.put("/api/assemblies/{id}", assemblyController::updateAssembly);
        app.delete("/api/assemblies/{id}", assemblyController::deleteAssembly);
        app.post("/api/assemblies/{id}/devices", assemblyController::addDeviceToAssembly);
        app.delete("/api/assemblies/{id}/devices/{deviceId}", assemblyController::removeDeviceFromAssembly);
        app.get("/api/assemblies/{id}/devices", assemblyController::getAssemblyDevices);
        app.get("/api/devices/{deviceId}/assemblies", assemblyController::getAssembliesByDevice);

        app.get("/api/radar/test", radarController::testConnection);
        app.post("/api/radar/test", radarController::testConnection);
        app.get("/api/radar/devices", radarController::getRadarDevices);
        app.post("/api/radar/devices", radarController::addRadarDevice);
        app.put("/api/radar/devices/{deviceId}", radarController::updateRadarDevice);
        app.delete("/api/radar/devices/{deviceId}", radarController::deleteRadarDevice);
        app.post("/api/radar/{deviceId}/background/start", radarController::startBackgroundCollection);
        app.post("/api/radar/{deviceId}/background/stop", radarController::stopBackgroundCollection);
        app.get("/api/radar/{deviceId}/background/status", radarController::getBackgroundStatus);
        app.get("/api/radar/{deviceId}/background/collecting/points", radarController::getCollectingPointCloud);
        app.get("/api/radar/{deviceId}/background/{backgroundId}/points", radarController::getBackgroundPoints);
        app.get("/api/radar/{deviceId}/zones", radarController::getZones);
        app.post("/api/radar/{deviceId}/zones", radarController::createZone);
        app.put("/api/radar/{deviceId}/zones/{zoneId}", radarController::updateZone);
        app.delete("/api/radar/{deviceId}/zones/{zoneId}", radarController::deleteZone);
        app.put("/api/radar/{deviceId}/zones/{zoneId}/toggle", radarController::toggleZone);
        app.get("/api/radar/{deviceId}/detection", radarController::getDetectionEnabled);
        app.put("/api/radar/{deviceId}/detection", radarController::setDetectionEnabled);
        app.get("/api/radar/{deviceId}/backgrounds", radarController::getBackgrounds);
        app.delete("/api/radar/{deviceId}/backgrounds/{backgroundId}", radarController::deleteBackground);
        app.get("/api/radar/{deviceId}/intrusions", radarController::getIntrusions);
        app.delete("/api/radar/{deviceId}/intrusions", radarController::clearIntrusions);
        app.get("/api/radar/intrusions/{id}/data", radarController::getIntrusionData);

        // 白名单（空间排除区）API
        app.get("/api/radar/{deviceId}/zones/{zoneId}/whitelist", radarController::getWhitelist);
        app.post("/api/radar/{deviceId}/zones/{zoneId}/whitelist", radarController::addWhitelist);
        app.delete("/api/radar/{deviceId}/zones/{zoneId}/whitelist/{exclusionId}", radarController::removeWhitelist);
        app.delete("/api/radar/{deviceId}/zones/{zoneId}/whitelist", radarController::clearWhitelist);
        app.get("/api/radar/{deviceId}/zones/{zoneId}/targets", radarController::getActiveTargets);

        EventTypeController eventTypeController = new EventTypeController(database);
        app.get("/api/event-types", eventTypeController::getAllEventTypes);
        app.get("/api/event-types/all", eventTypeController::getAllEventTypesList);
        app.get("/api/event-types/{brand}", eventTypeController::getEventTypesByBrand);

        com.digital.video.gateway.api.EventLibraryController eventLibraryController = new com.digital.video.gateway.api.EventLibraryController(database);
        app.get("/api/event-library/events", eventLibraryController::listEvents);
        app.get("/api/event-library/events/{id}", eventLibraryController::getEvent);
        app.post("/api/event-library/events", eventLibraryController::createEvent);
        app.put("/api/event-library/events/{id}", eventLibraryController::updateEvent);
        app.delete("/api/event-library/events/{id}", eventLibraryController::deleteEvent);
        app.post("/api/event-library/events/{id}/mappings", eventLibraryController::addMapping);
        app.delete("/api/event-library/events/{id}/mappings/{mappingId}", eventLibraryController::deleteMapping);

        app.get("/api/alarm-rules", alarmRuleController::getAlarmRules);
        app.get("/api/alarm-rules/{id}", alarmRuleController::getAlarmRule);
        app.post("/api/alarm-rules", alarmRuleController::createAlarmRule);
        app.put("/api/alarm-rules/{id}", alarmRuleController::updateAlarmRule);
        app.delete("/api/alarm-rules/{id}", alarmRuleController::deleteAlarmRule);
        app.put("/api/alarm-rules/{id}/toggle", alarmRuleController::toggleRule);
        app.get("/api/devices/{deviceId}/alarm-rules", alarmRuleController::getDeviceRules);
        app.get("/api/assemblies/{assemblyId}/alarm-rules", alarmRuleController::getAssemblyRules);

        app.get("/api/alarm-records", alarmRecordController::getAlarmRecords);
        app.get("/api/alarm-records/{id}", alarmRecordController::getAlarmRecord);

        app.get("/api/speakers", speakerController::getSpeakers);
        app.get("/api/speakers/{deviceId}", speakerController::getSpeaker);
        app.post("/api/speakers", speakerController::createSpeaker);
        app.put("/api/speakers/{deviceId}", speakerController::updateSpeaker);
        app.delete("/api/speakers/{deviceId}", speakerController::deleteSpeaker);
        app.post("/api/speakers/{deviceId}/play", speakerController::playVoice);

        app.get("/api/recording-tasks", recordingTaskController::getRecordingTasks);
        app.get("/api/recording-tasks/{taskId}", recordingTaskController::getRecordingTask);
        app.post("/api/recording-tasks", recordingTaskController::createRecordingTask);
        app.put("/api/recording-tasks/{taskId}", recordingTaskController::updateRecordingTask);
        app.post("/api/recording-tasks/download", recordingTaskController::downloadRecording);
        app.get("/api/recording-tasks/{taskId}/file", recordingTaskController::downloadRecordingFile);

        app.get("/api/flows", flowController::listFlows);
        app.get("/api/flows/{flowId}", flowController::getFlow);
        app.post("/api/flows", flowController::createFlow);
        app.put("/api/flows/{flowId}", flowController::updateFlow);
        app.delete("/api/flows/{flowId}", flowController::deleteFlow);
        app.post("/api/flows/{flowId}/test", flowController::testFlow);

        // 静态文件服务：/api/static/<path>（<> 允许多段路径如 captures/xxx.png、tts/xxx.mp3）
        app.get("/api/static/<path>", ctx -> {
            String path = ctx.pathParam("path");
            if (path.contains("..")) {
                ctx.status(403).result("Forbidden");
                return;
            }
            String baseDir = path.startsWith("tts/") ? "storage" : "data";
            java.io.File file = new java.io.File(baseDir + "/" + path);
            if (!file.exists() || !file.isFile()) {
                ctx.status(404).result("Not found");
                return;
            }
            String name = file.getName().toLowerCase();
            if (name.endsWith(".mp3")) ctx.contentType("audio/mpeg");
            else if (name.endsWith(".wav")) ctx.contentType("audio/wav");
            else if (name.endsWith(".jpg") || name.endsWith(".jpeg")) ctx.contentType("image/jpeg");
            else if (name.endsWith(".png")) ctx.contentType("image/png");
            else if (name.endsWith(".mp4")) ctx.contentType("video/mp4");
            else if (name.endsWith(".webm")) ctx.contentType("video/webm");
            else ctx.contentType("application/octet-stream");
            ctx.header("Content-Length", String.valueOf(file.length()));
            try (java.io.InputStream is = new java.io.FileInputStream(file)) {
                byte[] buf = new byte[8192];
                int n;
                java.io.OutputStream os = ctx.res().getOutputStream();
                while ((n = is.read(buf)) != -1) os.write(buf, 0, n);
                os.flush();
            }
        });

        // 异常处理
        app.exception(HaltException.class, (e, ctx) -> {
            ctx.status(e.getStatus()).result(e.getBody());
        });
        app.exception(Exception.class, (e, ctx) -> {
            logger.error("处理请求时发生异常", e);
            ctx.status(500).contentType("application/json");
            try {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("code", 500);
                errorResponse.put("message", "Internal server error: " + e.getMessage());
                errorResponse.put("data", null);
                ctx.result(objectMapper.writeValueAsString(errorResponse));
            } catch (Exception ex) {
                ctx.result("{\"code\":500,\"message\":\"Internal error\",\"data\":null}");
            }
        });

        app.start(port);
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

            // 停止 PTZ 监控服务
            if (ptzMonitorService != null) {
                ptzMonitorService.stop();
            }

            // 停止雷达服务（含点云线程池、WebSocket 发送池、PTZ 抓拍调度器）
            if (radarService != null) {
                radarService.stop();
            }

            // 停止设备状态统计任务
            if (statisticsScheduler != null) {
                statisticsScheduler.shutdown();
                try {
                    if (!statisticsScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                        statisticsScheduler.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    statisticsScheduler.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            if (playbackCleanupScheduler != null) {
                playbackCleanupScheduler.shutdown();
                try {
                    if (!playbackCleanupScheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                        playbackCleanupScheduler.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    playbackCleanupScheduler.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }

            // 关闭业务线程池（避免关闭后仍有任务访问数据库）
            if (recordingTaskService != null) {
                recordingTaskService.shutdown();
            }
            if (captureService != null) {
                captureService.shutdown();
            }
            FlowExecutor.shutdown();
            if (mqttMessageExecutor != null && !mqttMessageExecutor.isShutdown()) {
                mqttMessageExecutor.shutdown();
                try {
                    if (!mqttMessageExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                        mqttMessageExecutor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    mqttMessageExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
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

            // 停止 ZLM 内嵌服务
            if (zlmMediaService != null) {
                zlmMediaService.stop();
            }

            // 停止HTTP服务器
            if (app != null) app.stop();

            // 清理所有SDK
            SDKFactory.cleanup();

            logger.info("服务已关闭");
        } catch (Exception e) {
            logger.error("关闭服务时发生错误", e);
        }
    }
}
