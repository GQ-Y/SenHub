package com.hikvision.nvr;

import com.hikvision.nvr.config.Config;
import com.hikvision.nvr.config.ConfigLoader;
import com.hikvision.nvr.device.DeviceManager;
import com.hikvision.nvr.hikvision.HikvisionSDK;
import com.hikvision.nvr.keeper.Keeper;
import com.hikvision.nvr.mqtt.MqttClient;
import com.hikvision.nvr.scanner.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 海康威视NVR录像机控制服务主程序
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        logger.info("启动海康威视NVR控制服务...");

        try {
            // 加载配置
            Config config = ConfigLoader.load("config.yaml");
            logger.info("配置文件加载成功");

            // 初始化SDK
            HikvisionSDK sdk = HikvisionSDK.getInstance();
            if (!sdk.init(config.getSdk())) {
                logger.error("SDK初始化失败");
                System.exit(1);
            }
            logger.info("SDK初始化成功");

            // 初始化设备管理器
            DeviceManager deviceManager = new DeviceManager(config, sdk);
            deviceManager.init();
            logger.info("设备管理器初始化成功");

            // 初始化MQTT客户端
            MqttClient mqttClient = new MqttClient(config, deviceManager);
            if (!mqttClient.connect()) {
                logger.error("MQTT连接失败");
                System.exit(1);
            }
            logger.info("MQTT客户端连接成功");

            // 启动设备扫描器
            Scanner scanner = null;
            if (config.getScanner().isEnabled()) {
                scanner = new Scanner(config, deviceManager, sdk);
                scanner.start();
                logger.info("设备扫描器启动成功");
            }

            // 启动保活系统
            Keeper keeper = null;
            if (config.getKeeper().isEnabled()) {
                keeper = new Keeper(config, deviceManager, sdk);
                keeper.start();
                logger.info("保活系统启动成功");
            }

            // 注册JVM关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("正在关闭服务...");
                if (scanner != null) {
                    scanner.stop();
                }
                if (keeper != null) {
                    keeper.stop();
                }
                mqttClient.disconnect();
                deviceManager.cleanup();
                sdk.cleanup();
                logger.info("服务已关闭");
            }));

            logger.info("服务启动成功，等待命令...");

            // 主线程保持运行
            Thread.currentThread().join();

        } catch (Exception e) {
            logger.error("服务启动失败", e);
            System.exit(1);
        }
    }
}
