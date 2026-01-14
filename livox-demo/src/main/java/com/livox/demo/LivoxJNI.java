package com.livox.demo;

import java.io.File;

/**
 * Livox SDK JNI 封装类
 */
public class LivoxJNI {
    
    static {
        try {
            // 检测操作系统
            String osName = System.getProperty("os.name").toLowerCase();
            String osArch = System.getProperty("os.arch").toLowerCase();
            
            // 确定库文件名和路径
            String libName;
            String libDir;
            
            if (osName.contains("mac") || osName.contains("darwin")) {
                // macOS
                libName = "liblivoxjni.dylib";
                libDir = "lib/macos";
            } else {
                // Linux
                libName = "liblivoxjni.so";
                libDir = "lib/linux";
            }
            
            // 尝试从项目目录加载
            File libFile = new File(libDir, libName);
            if (libFile.exists()) {
                System.load(libFile.getAbsolutePath());
            } else {
                // 回退到系统库路径
                System.loadLibrary("livoxjni");
            }
        } catch (UnsatisfiedLinkError e) {
            System.err.println("无法加载 livoxjni 动态库: " + e.getMessage());
            System.err.println("请确保动态库在 java.library.path 中，或使用 -Djava.library.path 指定路径");
            throw e;
        }
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
}
