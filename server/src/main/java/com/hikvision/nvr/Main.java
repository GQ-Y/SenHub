package com.hikvision.nvr;

import com.hikvision.nvr.config.Config;
import com.hikvision.nvr.config.ConfigLoader;
import com.hikvision.nvr.hikvision.HikvisionSDK;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 海康威视NVR录像机控制服务主程序
 * 注意：此版本仅用于SDK集成测试，完整功能模块待实现
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        logger.info("启动海康威视NVR控制服务（SDK测试模式）...");

        try {
            // 加载配置
            Config config = ConfigLoader.load("config.yaml");
            logger.info("配置文件加载成功");

            // 初始化SDK
            HikvisionSDK sdk = HikvisionSDK.getInstance();
            if (!sdk.init(config.getSdk())) {
                logger.error("SDK初始化失败，错误码: {}", sdk.getLastError());
                System.exit(1);
            }
            logger.info("SDK初始化成功");

            // 注册JVM关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("正在关闭服务...");
                sdk.cleanup();
                logger.info("服务已关闭");
            }));

            logger.info("SDK测试模式启动成功，按Ctrl+C退出...");

            // 主线程保持运行
            Thread.currentThread().join();

        } catch (Exception e) {
            logger.error("服务启动失败", e);
            System.exit(1);
        }
    }
}
