package com.digital.video.gateway.Common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * 库路径辅助工具类
 * 用于根据系统架构获取正确的库文件路径
 * 
 * 目录结构：
 * lib/
 *   arm/
 *     hikvision/  (海康ARM库)
 *     dahua/      (大华ARM库)
 *   x86/
 *     hikvision/  (海康x86库)
 *     tiandy/     (天地伟业x86库，仅x86)
 *     dahua/      (大华x86库)
 * 
 * 注意：
 * - 海康：支持arm和x86
 * - 天地伟业：仅支持x86（在ARM系统上不加载）
 * - 大华：支持arm和x86
 */
public class LibraryPathHelper {
    private static final Logger logger = LoggerFactory.getLogger(LibraryPathHelper.class);
    
    /**
     * 获取当前系统架构对应的目录名
     * @return "arm" 或 "x86"
     */
    public static String getArchitectureDir() {
        String osArch = System.getProperty("os.arch");
        if (osArch == null) {
            logger.warn("无法获取系统架构，默认使用arm");
            return "arm";
        }
        
        String normalizedArch = ArchitectureChecker.normalizeArchitecture(osArch);
        if (normalizedArch.contains("aarch64") || normalizedArch.contains("arm")) {
            return "arm";
        } else if (normalizedArch.contains("x86_64") || normalizedArch.contains("x86") || normalizedArch.contains("amd64")) {
            return "x86";
        } else {
            logger.warn("未知的系统架构: {}，默认使用arm", osArch);
            return "arm";
        }
    }
    
    /**
     * 获取SDK库的基础路径（根据系统架构）
     * @param sdkName SDK名称：hikvision, tiandy, dahua
     * @return 库文件基础路径，例如：/path/to/lib/arm/hikvision
     * 
     * 注意：
     * - 海康：支持arm和x86
     * - 天地伟业：仅支持x86（在ARM系统上返回null）
     * - 大华：支持arm和x86
     */
    public static String getSDKLibPath(String sdkName) {
        String userDir = System.getProperty("user.dir");
        String archDir = getArchitectureDir();
        
        // 天地伟业仅支持x86架构
        if ("tiandy".equalsIgnoreCase(sdkName) && "arm".equals(archDir)) {
            logger.debug("天地伟业SDK仅支持x86架构，当前系统为ARM，返回null");
            return null;
        }
        
        return userDir + "/lib/" + archDir + "/" + sdkName;
    }
    
    /**
     * 获取SDK库的基础路径（根据系统架构和自定义基础路径）
     * @param basePath 基础路径，如果为null则使用user.dir
     * @param sdkName SDK名称：hikvision, tiandy, dahua
     * @return 库文件基础路径，如果SDK不支持当前架构则返回null
     */
    public static String getSDKLibPath(String basePath, String sdkName) {
        String archDir = getArchitectureDir();
        
        // 天地伟业仅支持x86架构
        if ("tiandy".equalsIgnoreCase(sdkName) && "arm".equals(archDir)) {
            logger.debug("天地伟业SDK仅支持x86架构，当前系统为ARM，返回null");
            return null;
        }
        
        if (basePath == null || basePath.isEmpty()) {
            return getSDKLibPath(sdkName);
        }
        
        File basePathFile = new File(basePath);
        if (basePathFile.isAbsolute()) {
            return basePath + "/" + archDir + "/" + sdkName;
        } else {
            String userDir = System.getProperty("user.dir");
            return userDir + "/" + basePath + "/" + archDir + "/" + sdkName;
        }
    }
    
    /**
     * 构建完整的java.library.path，包含所有SDK库目录
     * @return 完整的库路径字符串
     */
    public static String buildLibraryPath() {
        String userDir = System.getProperty("user.dir");
        String archDir = getArchitectureDir();
        
        StringBuilder libPathBuilder = new StringBuilder();
        
        // 添加各SDK的库目录
        libPathBuilder.append(userDir).append("/lib/").append(archDir).append("/hikvision");
        libPathBuilder.append(":").append(userDir).append("/lib/").append(archDir).append("/hikvision/HCNetSDKCom");
        
        // 天地伟业仅支持x86，在ARM系统上不添加
        if ("x86".equals(archDir)) {
            libPathBuilder.append(":").append(userDir).append("/lib/").append(archDir).append("/tiandy");
        }
        
        libPathBuilder.append(":").append(userDir).append("/lib/").append(archDir).append("/dahua");
        libPathBuilder.append(":").append(userDir).append("/lib/").append(archDir);
        libPathBuilder.append(":").append(userDir).append("/lib");
        
        // 添加当前java.library.path
        String currentLibPath = System.getProperty("java.library.path");
        if (currentLibPath != null && !currentLibPath.isEmpty()) {
            libPathBuilder.append(":").append(currentLibPath);
        }
        
        return libPathBuilder.toString();
    }
    
    /**
     * 检查指定SDK的库目录是否存在
     * @param sdkName SDK名称
     * @return true表示目录存在
     */
    public static boolean checkSDKLibDirExists(String sdkName) {
        String libPath = getSDKLibPath(sdkName);
        File libDir = new File(libPath);
        return libDir.exists() && libDir.isDirectory();
    }
}
