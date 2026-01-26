package com.digital.video.gateway.driver.livox;

import com.digital.video.gateway.Common.LibraryPathHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Livox SDK JNI 封装类
 */
public class LivoxJNI {
    
    private static final Logger log = LoggerFactory.getLogger(LivoxJNI.class);
    
    // 标记库是否已成功加载
    private static boolean libraryLoaded = false;
    private static String loadError = null;
    
    static {
        try {
            // 检测操作系统
            String osName = System.getProperty("os.name").toLowerCase();
            String osArch = System.getProperty("os.arch");
            
            log.info("开始加载 Livox JNI 库 - 操作系统: {}, 架构: {}", osName, osArch);
            
            // 确定库文件名和路径
            String libName;
            String libDir;
            
            if (osName.contains("mac") || osName.contains("darwin")) {
                // macOS
                libName = "liblivoxjni.dylib";
                libDir = LibraryPathHelper.getLivoxLibPath("macos");
            } else {
                // Linux
                libName = "liblivoxjni.so";
                libDir = LibraryPathHelper.getLivoxLibPath("linux");
            }
            
            log.info("Livox JNI 库路径: {}/{}", libDir, libName);
            
            // 尝试从项目目录加载
            File libFile = new File(libDir, libName);
            if (libFile.exists()) {
                log.info("找到库文件: {}, 大小: {} bytes", libFile.getAbsolutePath(), libFile.length());
                
                // 检查并先加载依赖库 liblivox_lidar_sdk_shared.so
                String depLibName = osName.contains("mac") || osName.contains("darwin") 
                    ? "liblivox_lidar_sdk_shared.dylib" 
                    : "liblivox_lidar_sdk_shared.so";
                File depLibFile = new File(libDir, depLibName);
                
                if (depLibFile.exists()) {
                    log.info("找到依赖库: {}, 大小: {} bytes", depLibFile.getAbsolutePath(), depLibFile.length());
                    
                    // 先加载依赖库，这样主库加载时就能找到它
                    try {
                        System.load(depLibFile.getAbsolutePath());
                        log.info("✓ 成功预加载依赖库: {}", depLibFile.getAbsolutePath());
                    } catch (UnsatisfiedLinkError e) {
                        log.warn("预加载依赖库失败（可能已加载）: {}", e.getMessage());
                        // 继续尝试加载主库，依赖库可能已经在系统路径中
                    }
                } else {
                    log.warn("依赖库不存在: {}, 尝试从系统路径查找", depLibFile.getAbsolutePath());
                    // 尝试从系统路径加载依赖库
                    try {
                        System.loadLibrary("livox_lidar_sdk_shared");
                        log.info("✓ 从系统路径加载依赖库成功");
                    } catch (UnsatisfiedLinkError e) {
                        log.warn("从系统路径加载依赖库失败: {}", e.getMessage());
                        log.warn("这可能导致主库加载失败");
                    }
                }
                
                // 加载JNI主库（使用绝对路径）
                try {
                    System.load(libFile.getAbsolutePath());
                    log.info("✓ 成功加载 Livox JNI 库: {}", libFile.getAbsolutePath());
                    libraryLoaded = true;
                } catch (UnsatisfiedLinkError e) {
                    log.error("✗ 加载库文件失败: {}", e.getMessage());
                    log.error("可能的原因:");
                    if (e.getMessage().contains("cannot open shared object file")) {
                        log.error("  1. 依赖库缺失或路径不正确: {}", depLibName);
                        log.error("     - 确保 {} 在 {} 目录中", depLibName, libDir);
                        log.error("     - 或设置 LD_LIBRARY_PATH 环境变量指向包含依赖库的目录");
                    } else if (e.getMessage().contains("wrong ELF class")) {
                        log.error("  1. 架构不匹配: 库文件架构与系统架构不一致");
                        log.error("     - 系统架构: {}", System.getProperty("os.arch"));
                        log.error("     - 使用 'file {}' 检查库文件架构", libFile.getAbsolutePath());
                    } else {
                        log.error("  1. 依赖库缺失: {}", depLibName);
                        log.error("  2. 架构不匹配: 库文件架构与系统架构不一致");
                        log.error("  3. 缺少系统依赖库（如 libc, libpthread 等）");
                        log.error("  4. 权限问题: 库文件没有执行权限");
                    }
                    log.error("建议检查: ldd {} 查看依赖关系", libFile.getAbsolutePath());
                    loadError = "加载失败: " + e.getMessage() + " (库文件存在但无法加载)";
                }
            } else {
                log.warn("✗ Livox JNI 库文件不存在: {}", libFile.getAbsolutePath());
                log.warn("尝试从系统库路径加载...");
                
                // 回退到系统库路径
                try {
                    System.loadLibrary("livoxjni");
                    log.info("✓ 已通过 System.loadLibrary 加载 livoxjni");
                    libraryLoaded = true;
                } catch (UnsatisfiedLinkError e2) {
                    log.error("✗ 从系统库路径加载 livoxjni 也失败: {}", e2.getMessage());
                    loadError = "库文件不存在且系统库路径中也没有: " + e2.getMessage();
                }
            }
        } catch (UnsatisfiedLinkError e) {
            log.error("✗ 无法加载 livoxjni 动态库: {}", e.getMessage());
            log.error("请确保动态库在 java.library.path 中，或使用 -Djava.library.path 指定路径");
            loadError = e.getMessage();
            // 不抛出异常，允许类加载成功，但标记库未加载
        } catch (Exception e) {
            log.error("✗ 加载 Livox JNI 库时发生未知异常", e);
            loadError = e.getMessage();
        }
        
        if (!libraryLoaded) {
            log.error("Livox JNI 库未加载，雷达功能将不可用");
        }
    }
    
    /**
     * 检查库是否已成功加载
     * @return true 表示库已加载，false 表示未加载
     */
    public static boolean isLibraryLoaded() {
        return libraryLoaded;
    }
    
    /**
     * 获取库加载错误信息
     * @return 错误信息，如果加载成功则返回 null
     */
    public static String getLoadError() {
        return loadError;
    }

    /**
     * 初始化 Livox SDK
     * @param configPath 配置文件路径
     * @return 是否成功
     */
    public static native boolean init(String configPath);

    /**
     * 启动 SDK
     * @return 是否成功
     */
    public static native boolean start();

    /**
     * 停止 SDK
     */
    public static native void stop();

    /**
     * 设置点云数据回调
     * @param callback 回调接口
     */
    public static native void setPointCloudCallback(PointCloudCallback callback);
    
    /**
     * 设置设备信息变化回调
     * SDK 会自动检测设备连接/断开并通过此回调通知
     * @param callback 回调接口
     */
    public static native void setDeviceInfoCallback(DeviceInfoCallback callback);
    
    /**
     * 根据设备句柄获取设备序列号
     * @param handle 设备句柄
     * @return 设备序列号，如果不存在返回 null
     */
    public static native String getDeviceSerial(int handle);
    
    /**
     * 根据设备句柄获取设备 IP
     * @param handle 设备句柄
     * @return 设备 IP，如果不存在返回 null
     */
    public static native String getDeviceIp(int handle);
    
    /**
     * 获取所有已连接设备的句柄
     * @return 设备句柄数组
     */
    public static native int[] getConnectedDeviceHandles();
    
    /**
     * 根据 IP 地址获取设备序列号
     * @param ip 设备 IP 地址
     * @return 设备序列号，如果不存在返回 null
     */
    public static native String getDeviceSerialByIp(String ip);
}
