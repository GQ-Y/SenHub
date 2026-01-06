package com.hikvision.nvr;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.hikvision.nvr.scanner.DeviceScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

            // 4. 初始化设备管理器
            deviceManager = new DeviceManager(sdk, database, config.getDevice());
            logger.info("设备管理器初始化成功");

            // 5. 初始化MQTT客户端
            mqttClient = new MqttClient(config.getMqtt());
            setupMqttMessageHandler();
            if (!mqttClient.connect()) {
                logger.error("MQTT连接失败");
                System.exit(1);
            }
            logger.info("MQTT客户端连接成功");

            // 6. 初始化命令处理器
            commandHandler = new CommandHandler(deviceManager, sdk);
            logger.info("命令处理器初始化成功");

            // 7. 启动设备扫描器
            scanner = new DeviceScanner(sdk, database, config.getScanner(), config.getDevice());
            scanner.setDeviceFoundCallback(this::onDeviceFound);
            if (scanner.start()) {
                logger.info("设备扫描器启动成功");
            }

            // 8. 启动保活系统
            keeper = new Keeper(deviceManager, config.getKeeper());
            keeper.start();
            logger.info("保活系统启动成功");

            // 9. 注册JVM关闭钩子
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
