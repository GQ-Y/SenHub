package com.digital.video.gateway.driver.livox;

import com.digital.video.gateway.Common.LibraryPathHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Livox SDK 诊断工具
 * 用于检查库文件、依赖关系和加载状态
 */
public class LivoxDiagnostics {
    private static final Logger log = LoggerFactory.getLogger(LivoxDiagnostics.class);

    /**
     * 执行完整的诊断检查
     */
    public static void runDiagnostics() {
        log.info("=== Livox SDK 诊断开始 ===");
        
        // 1. 系统信息
        log.info("系统信息:");
        log.info("  操作系统: {}", System.getProperty("os.name"));
        log.info("  系统架构: {}", System.getProperty("os.arch"));
        log.info("  Java版本: {}", System.getProperty("java.version"));
        log.info("  工作目录: {}", System.getProperty("user.dir"));
        
        // 2. 库路径信息
        String osName = System.getProperty("os.name").toLowerCase();
        String libDir;
        String libName;
        String depLibName;
        
        if (osName.contains("mac") || osName.contains("darwin")) {
            libDir = LibraryPathHelper.getLivoxLibPath("macos");
            libName = "liblivoxjni.dylib";
            depLibName = "liblivox_lidar_sdk_shared.dylib";
        } else {
            libDir = LibraryPathHelper.getLivoxLibPath("linux");
            libName = "liblivoxjni.so";
            depLibName = "liblivox_lidar_sdk_shared.so";
        }
        
        log.info("库路径信息:");
        log.info("  库目录: {}", libDir);
        log.info("  JNI库名: {}", libName);
        log.info("  依赖库名: {}", depLibName);
        
        // 3. 检查库文件
        File libFile = new File(libDir, libName);
        File depLibFile = new File(libDir, depLibName);
        
        log.info("库文件检查:");
        if (libFile.exists()) {
            log.info("  ✓ JNI库存在: {}", libFile.getAbsolutePath());
            log.info("    大小: {} bytes", libFile.length());
            log.info("    可读: {}", libFile.canRead());
            log.info("    可执行: {}", libFile.canExecute());
        } else {
            log.error("  ✗ JNI库不存在: {}", libFile.getAbsolutePath());
        }
        
        if (depLibFile.exists()) {
            log.info("  ✓ 依赖库存在: {}", depLibFile.getAbsolutePath());
            log.info("    大小: {} bytes", depLibFile.length());
            log.info("    可读: {}", depLibFile.canRead());
            log.info("    可执行: {}", depLibFile.canExecute());
        } else {
            log.error("  ✗ 依赖库不存在: {}", depLibFile.getAbsolutePath());
            log.error("    这是导致加载失败的主要原因！");
        }
        
        // 4. 检查库加载状态
        log.info("库加载状态:");
        if (LivoxJNI.isLibraryLoaded()) {
            log.info("  ✓ JNI库已成功加载");
        } else {
            log.error("  ✗ JNI库未加载");
            String error = LivoxJNI.getLoadError();
            if (error != null) {
                log.error("    错误信息: {}", error);
            }
        }
        
        // 5. 环境变量
        log.info("环境变量:");
        String ldPath = System.getenv("LD_LIBRARY_PATH");
        if (ldPath != null && !ldPath.isEmpty()) {
            log.info("  LD_LIBRARY_PATH: {}", ldPath);
        } else {
            log.warn("  LD_LIBRARY_PATH: 未设置");
        }
        
        String javaLibPath = System.getProperty("java.library.path");
        if (javaLibPath != null && !javaLibPath.isEmpty()) {
            log.info("  java.library.path: {}", javaLibPath);
        } else {
            log.warn("  java.library.path: 未设置");
        }
        
        // 6. 建议
        log.info("诊断建议:");
        if (!libFile.exists()) {
            log.warn("  1. 确保 {} 文件存在", libFile.getAbsolutePath());
            log.warn("  2. 检查库文件是否已编译并复制到正确位置");
        }
        if (!depLibFile.exists()) {
            log.warn("  1. 确保 {} 文件存在", depLibFile.getAbsolutePath());
            log.warn("  2. 这是 Livox SDK 的核心库，必须存在");
        }
        if (!LivoxJNI.isLibraryLoaded()) {
            log.warn("  1. 检查库文件架构是否与系统架构匹配");
            log.warn("  2. 在 Linux 上，可以使用 'file {}' 检查架构", libFile.getAbsolutePath());
            log.warn("  3. 使用 'ldd {}' 检查依赖关系", libFile.getAbsolutePath());
            log.warn("  4. 确保所有依赖库都已安装（如 libc, libpthread 等）");
        }
        
        log.info("=== Livox SDK 诊断完成 ===");
    }
}
