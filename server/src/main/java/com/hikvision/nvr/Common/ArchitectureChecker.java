package com.hikvision.nvr.Common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

/**
 * 架构检查工具类
 * 用于检查库文件的架构是否与当前系统架构匹配
 */
public class ArchitectureChecker {
    private static final Logger logger = LoggerFactory.getLogger(ArchitectureChecker.class);
    
    /**
     * 检查库文件架构是否与系统架构匹配
     * 
     * @param libFile 库文件
     * @return true表示架构匹配，false表示不匹配或检查失败
     */
    public static boolean checkArchitecture(File libFile) {
        if (libFile == null || !libFile.exists()) {
            return false;
        }
        
        try {
            // 获取系统架构
            String osArch = System.getProperty("os.arch");
            if (osArch == null) {
                logger.warn("无法获取系统架构信息");
                return true; // 无法检查时，允许继续加载
            }
            
            // 使用file命令检查库文件架构
            String libArch = getLibraryArchitecture(libFile);
            if (libArch == null) {
                logger.warn("无法检查库文件架构: {}", libFile.getAbsolutePath());
                return true; // 无法检查时，允许继续加载
            }
            
            // 标准化架构名称
            String normalizedOsArch = normalizeArchitecture(osArch);
            String normalizedLibArch = normalizeArchitecture(libArch);
            
            // 检查是否匹配
            boolean matches = normalizedOsArch.equals(normalizedLibArch);
            
            if (!matches) {
                logger.warn("库文件架构不匹配：系统架构={}，库文件架构={}，库文件路径={}", 
                    osArch, libArch, libFile.getAbsolutePath());
                logger.warn("如需使用此SDK，请提供与系统架构匹配的库文件");
                return false;
            }
            
            logger.debug("库文件架构检查通过：系统架构={}，库文件架构={}", osArch, libArch);
            return true;
            
        } catch (Exception e) {
            logger.warn("检查库文件架构时发生异常: {}", e.getMessage());
            return true; // 检查失败时，允许继续加载（避免因检查工具问题导致无法加载）
        }
    }
    
    /**
     * 使用file命令获取库文件的架构信息
     */
    private static String getLibraryArchitecture(File libFile) {
        try {
            ProcessBuilder pb = new ProcessBuilder("file", libFile.getAbsolutePath());
            Process process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null) {
                    // file命令输出格式示例：
                    // libdhnetsdk.so: ELF 64-bit LSB shared object, ARM aarch64, version 1 (SYSV), dynamically linked, ...
                    // 或
                    // libdhnetsdk.so: ELF 64-bit LSB shared object, x86-64, version 1 (SYSV), dynamically linked, ...
                    
                    if (line.contains("ARM aarch64") || line.contains("aarch64")) {
                        return "aarch64";
                    } else if (line.contains("ARM") || line.contains("arm")) {
                        return "arm";
                    } else if (line.contains("x86-64") || line.contains("x86_64") || line.contains("amd64")) {
                        return "x86_64";
                    } else if (line.contains("i386") || line.contains("i686") || line.contains("x86")) {
                        return "x86";
                    }
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                logger.warn("file命令执行失败，退出码: {}", exitCode);
                return null;
            }
            
        } catch (Exception e) {
            logger.debug("无法使用file命令检查库文件架构: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 标准化架构名称
     */
    private static String normalizeArchitecture(String arch) {
        if (arch == null) {
            return "unknown";
        }
        
        arch = arch.toLowerCase().trim();
        
        // ARM架构
        if (arch.contains("aarch64") || arch.equals("arm64")) {
            return "aarch64";
        }
        if (arch.contains("arm")) {
            return "arm";
        }
        
        // x86架构
        if (arch.contains("x86_64") || arch.contains("x86-64") || arch.equals("amd64")) {
            return "x86_64";
        }
        if (arch.contains("x86") || arch.contains("i386") || arch.contains("i686")) {
            return "x86";
        }
        
        return arch;
    }
}
