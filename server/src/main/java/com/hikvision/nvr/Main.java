package com.hikvision.nvr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hikvision.nvr.api.*;
import com.hikvision.nvr.auth.AuthFilter;
import com.hikvision.nvr.command.CommandHandler;
import com.hikvision.nvr.command.CommandResponse;
import com.hikvision.nvr.config.Config;
import com.hikvision.nvr.config.ConfigLoader;
import com.hikvision.nvr.database.Database;
import com.hikvision.nvr.database.DeviceInfo;
import com.hikvision.nvr.device.DeviceManager;
import com.hikvision.nvr.hikvision.HikvisionSDK;
import com.hikvision.nvr.keeper.Keeper;
import com.hikvision.nvr.mqtt.MqttClient;
import com.hikvision.nvr.recorder.Recorder;
import com.hikvision.nvr.scanner.DeviceScanner;
import com.hikvision.nvr.service.ConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;

import java.util.HashMap;
import java.util.Map;

/**
 * 海康威视NVR录像机控制服务主程序
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    
    private Config config;
    private HikvisionSDK sdk;
    private Database database;
    private MqttClient mqttClient;
    private DeviceManager deviceManager;
    private DeviceScanner scanner;
    private CommandHandler commandHandler;
    private Keeper keeper;
    private Recorder recorder;
    private ConfigService configService;
    private ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) {
        Main main = new Main();
        main.start();
    }

    /**
     * 启动服务
     */
    public void start() {
        logger.info("启动海康威视NVR控制服务...");

        try {
            // 1. 加载配置
            config = ConfigLoader.load("config.yaml");
            if (config == null) {
                logger.error("配置文件加载失败");
                System.exit(1);
            }
            logger.info("配置文件加载成功");

            // 2. 初始化SDK
            sdk = HikvisionSDK.getInstance();
            if (!sdk.init(config.getSdk())) {
                logger.error("SDK初始化失败，错误码: {}", sdk.getLastError());
                System.exit(1);
            }
            logger.info("SDK初始化成功");

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
                    sdkConfig.getLibPath() != null ? sdkConfig.getLibPath() : "./MakeAll",
                    sdkConfig.getLogPath() != null ? sdkConfig.getLogPath() : "./sdkLog",
                    sdkConfig.getLogLevel() > 0 ? sdkConfig.getLogLevel() : 3,
                    "ACTIVE"
                );
                logger.info("默认SDK驱动配置已初始化");
            }

            // 4. 初始化设备管理器
            deviceManager = new DeviceManager(sdk, database, config.getDevice());
            logger.info("设备管理器初始化成功");

            // 5. 初始化录制管理器
            recorder = new Recorder(sdk, deviceManager, config.getRecorder());
            recorder.start();
            logger.info("录制管理器初始化成功");

            // 6. 初始化MQTT客户端
            mqttClient = new MqttClient(config.getMqtt());
            setupMqttMessageHandler();
            if (!mqttClient.connect()) {
                logger.error("MQTT连接失败");
                System.exit(1);
            }
            logger.info("MQTT客户端连接成功");

            // 7. 初始化命令处理器
            commandHandler = new CommandHandler(deviceManager, sdk, recorder);
            logger.info("命令处理器初始化成功");

            // 8. 启动设备扫描器
            scanner = new DeviceScanner(sdk, database, config.getScanner(), config.getDevice());
            scanner.setDeviceFoundCallback(this::onDeviceFound);
            if (scanner.start()) {
                logger.info("设备扫描器启动成功");
            }

            // 9. 启动保活系统
            keeper = new Keeper(deviceManager, config.getKeeper());
            keeper.start();
            logger.info("保活系统启动成功");

            // 10. 启动HTTP服务器
            startHttpServer();
            logger.info("HTTP服务器启动成功");

            // 11. 注册JVM关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

            logger.info("========================================");
            logger.info("海康威视NVR控制服务启动成功！");
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
     * 设备发现回调
     */
    private void onDeviceFound(DeviceInfo device) {
        try {
            logger.info("发现新设备: {}", device);
            
            // 尝试自动登录
            if (deviceManager.loginDevice(device)) {
                // 发布设备上线状态
                publishDeviceStatus(device, "online");
                
                // 如果录制功能启用，自动启动录制
                if (recorder != null && config.getRecorder() != null && config.getRecorder().isEnabled()) {
                    recorder.startRecording(device.getDeviceId());
                }
            } else {
                // 发布设备离线状态
                publishDeviceStatus(device, "offline");
            }
        } catch (Exception e) {
            logger.error("处理设备发现失败", e);
        }
    }

    /**
     * 发布设备状态
     */
    private void publishDeviceStatus(DeviceInfo device, String status) {
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
        int port = 8080; // 默认端口，可以从配置中读取
        
        Spark.port(port);
        
        // 设置CORS
        Spark.before((request, response) -> {
            response.header("Access-Control-Allow-Origin", "*");
            response.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            response.header("Access-Control-Allow-Headers", "Content-Type, Authorization");
            response.type("application/json");
        });
        
        // 处理OPTIONS请求
        Spark.options("/*", (request, response) -> {
            return "";
        });
        
        // 注册认证过滤器
        Spark.before(new AuthFilter());
        
        // 初始化控制器
        AuthController authController = new AuthController(database);
        DeviceController deviceController = new DeviceController(deviceManager, sdk, database);
        DriverController driverController = new DriverController(database);
        MqttController mqttController = new MqttController(configService, mqttClient);
        SystemController systemController = new SystemController(configService);
        DashboardController dashboardController = new DashboardController(deviceManager);
        
        // 注册路由
        // 认证路由
        Spark.post("/api/auth/login", authController::login);
        
        // 设备路由
        Spark.get("/api/devices", deviceController::getDevices);
        Spark.get("/api/devices/:id", deviceController::getDevice);
        Spark.post("/api/devices", deviceController::addDevice);
        Spark.put("/api/devices/:id", deviceController::updateDevice);
        Spark.delete("/api/devices/:id", deviceController::deleteDevice);
        Spark.post("/api/devices/:id/reboot", deviceController::rebootDevice);
        Spark.post("/api/devices/:id/snapshot", deviceController::captureSnapshot);
        Spark.get("/api/devices/:id/snapshot/file", deviceController::getSnapshotFile);
        Spark.post("/api/devices/:id/ptz", deviceController::ptzControl);
        Spark.get("/api/devices/:id/stream", deviceController::getStreamUrl);
        Spark.post("/api/devices/:id/playback", deviceController::playback);
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
        
        // 仪表板路由
        Spark.get("/api/dashboard/stats", dashboardController::getStats);
        Spark.get("/api/dashboard/chart", dashboardController::getChart);
        
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
            
            // 清理SDK
            if (sdk != null) {
                sdk.cleanup();
            }

            logger.info("服务已关闭");
        } catch (Exception e) {
            logger.error("关闭服务时发生错误", e);
        }
    }
}
