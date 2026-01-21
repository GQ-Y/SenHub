package com.digital.video.gateway.Common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * 日志清理工具类
 * 用于在服务启动时清理旧的日志文件
 */
public class LogCleaner {
    private static final Logger logger = LoggerFactory.getLogger(LogCleaner.class);

    /**
     * 清理日志目录中的所有日志文件
     * @param logDir 日志目录路径
     * @return 清理的文件数量
     */
    public static int cleanLogDirectory(String logDir) {
        int cleanedCount = 0;
        try {
            Path logPath = Paths.get(logDir);
            if (!Files.exists(logPath) || !Files.isDirectory(logPath)) {
                logger.debug("日志目录不存在，跳过清理: {}", logDir);
                return 0;
            }

            try (Stream<Path> paths = Files.walk(logPath)) {
                cleanedCount = (int) paths
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String fileName = path.getFileName().toString();
                        // 只清理旧的滚动日志文件，不删除当前正在使用的日志文件
                        // 保留 app.log 和 sdk.log（当前日志文件）
                        if (fileName.equals("app.log") || fileName.equals("sdk.log")) {
                            return false;
                        }
                        // 清理旧的滚动日志文件（带日期后缀的）
                        return fileName.endsWith(".log") || 
                               fileName.endsWith(".log.gz") ||
                               fileName.endsWith(".log.zip");
                    })
                    .mapToInt(path -> {
                        try {
                            Files.delete(path);
                            logger.debug("已删除日志文件: {}", path);
                            return 1;
                        } catch (IOException e) {
                            logger.warn("删除日志文件失败: {}, 错误: {}", path, e.getMessage());
                            return 0;
                        }
                    })
                    .sum();
            }

            if (cleanedCount > 0) {
                logger.info("已清理 {} 个日志文件，目录: {}", cleanedCount, logDir);
            }
        } catch (Exception e) {
            logger.error("清理日志目录失败: {}, 错误: {}", logDir, e.getMessage());
        }
        return cleanedCount;
    }

    /**
     * 清理SDK日志目录中的所有日志文件
     * @param sdkLogDir SDK日志目录路径
     * @return 清理的文件数量
     */
    public static int cleanSdkLogDirectory(String sdkLogDir) {
        int cleanedCount = 0;
        try {
            Path sdkLogPath = Paths.get(sdkLogDir);
            if (!Files.exists(sdkLogPath) || !Files.isDirectory(sdkLogPath)) {
                logger.debug("SDK日志目录不存在，跳过清理: {}", sdkLogDir);
                return 0;
            }

            try (Stream<Path> paths = Files.walk(sdkLogPath)) {
                cleanedCount = (int) paths
                    .filter(Files::isRegularFile)
                    .mapToInt(path -> {
                        try {
                            Files.delete(path);
                            logger.debug("已删除SDK日志文件: {}", path);
                            return 1;
                        } catch (IOException e) {
                            logger.warn("删除SDK日志文件失败: {}, 错误: {}", path, e.getMessage());
                            return 0;
                        }
                    })
                    .sum();
            }

            if (cleanedCount > 0) {
                logger.info("已清理 {} 个SDK日志文件，目录: {}", cleanedCount, sdkLogDir);
            }
        } catch (Exception e) {
            logger.error("清理SDK日志目录失败: {}, 错误: {}", sdkLogDir, e.getMessage());
        }
        return cleanedCount;
    }

    /**
     * 清理所有日志（应用日志和SDK日志）
     * @param logDir 应用日志目录
     * @param sdkLogDir SDK日志目录
     * @return 总共清理的文件数量
     */
    public static int cleanAllLogs(String logDir, String sdkLogDir) {
        int totalCleaned = 0;
        totalCleaned += cleanLogDirectory(logDir);
        totalCleaned += cleanSdkLogDirectory(sdkLogDir);
        return totalCleaned;
    }
}
