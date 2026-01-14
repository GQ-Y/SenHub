package com.livox.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Livox Mid-360 激光雷达 Demo 主程序
 * 基于官方 SDK (JNI) 实现通信连接和数据采集
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private LivoxSDKDriver driver;
    private PointCloudStats stats;

    public static void main(String[] args) {
        Main main = new Main();
        main.run();
    }

    public void run() {
        log.info("========================================");
        log.info("Livox Mid-360 激光雷达 Demo");
        log.info("功能: 通信连接 + 数据采集 (基于官方SDK)");
        log.info("========================================");

        // 初始化统计类
        stats = new PointCloudStats();

        // 获取配置文件路径
        String configPath = getConfigPath();
        log.info("使用配置文件: {}", configPath);

        // 初始化驱动
        driver = new LivoxSDKDriver(stats);

        // 启动驱动
        try {
            if (!driver.start(configPath)) {
                log.error("驱动启动失败，程序退出");
                return;
            }

            log.info("等待接收点云数据...");
            log.info("提示: 按 Ctrl+C 退出程序");
            log.info("");

            // 保持程序运行
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("");
                log.info("正在关闭...");
                shutdown();
            }));

            // 主线程等待
            Thread.currentThread().join();

        } catch (InterruptedException e) {
            log.info("程序被中断");
            shutdown();
        } catch (Exception e) {
            log.error("程序运行出错", e);
            shutdown();
        }
    }

    /**
     * 获取配置文件路径
     */
    private String getConfigPath() {
        // 优先使用当前目录下的 config.json
        File configFile = new File("config.json");
        if (configFile.exists()) {
            return configFile.getAbsolutePath();
        }

        // 其次使用类路径下的配置文件
        String classPathConfig = getClass().getResource("/config.json") != null 
            ? getClass().getResource("/config.json").getPath() 
            : null;

        if (classPathConfig != null) {
            return classPathConfig;
        }

        // 默认返回当前目录
        return "config.json";
    }

    /**
     * 关闭程序
     */
    private void shutdown() {
        if (driver != null) {
            driver.stop();
        }
        log.info("程序已退出");
        log.info("统计摘要: {}", stats.getSummary());
    }
}
