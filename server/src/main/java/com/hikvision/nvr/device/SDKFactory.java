package com.hikvision.nvr.device;

import com.hikvision.nvr.config.Config;
import com.hikvision.nvr.dahua.DahuaSDK;
import com.hikvision.nvr.hikvision.HikvisionSDK;
import com.hikvision.nvr.tiandy.TiandySDK;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SDK工厂类
 * 负责创建和管理不同品牌的SDK实例
 */
public class SDKFactory {
    private static final Logger logger = LoggerFactory.getLogger(SDKFactory.class);
    
    private static HikvisionSDK hikvisionSDK;
    private static TiandySDK tiandySDK;
    private static DahuaSDK dahuaSDK;
    
    private static boolean initialized = false;
    
    /**
     * 初始化所有SDK
     */
    public static void init(Config config) {
        if (initialized) {
            return;
        }
        
        try {
            // 预先设置大华SDK库路径，避免NetSDKLib静态初始化时找不到库
            // 必须在任何SDK初始化之前设置，因为NetSDKLib.NETSDK_INSTANCE是静态的
            try {
                String libDir = System.getProperty("user.dir") + "/lib/dahua";
                java.io.File libDirFile = new java.io.File(libDir);
                if (libDirFile.exists()) {
                    com.hikvision.nvr.dahua.lib.LibraryLoad.setExtractPath(libDir);
                    logger.debug("大华SDK库路径已设置: {}", libDir);
                }
            } catch (Exception e) {
                logger.debug("设置大华SDK库路径失败: {}", e.getMessage());
            }
            
            // 初始化海康SDK
            if (config.getSdk() != null) {
                hikvisionSDK = HikvisionSDK.getInstance();
                if (hikvisionSDK.init(config.getSdk())) {
                    logger.info("海康SDK初始化成功");
                } else {
                    logger.warn("海康SDK初始化失败");
                }
            }
            
            // 初始化天地伟业SDK
            if (config.getSdk() != null) {
                tiandySDK = TiandySDK.getInstance();
                // 使用相同的配置，但可以后续扩展为独立配置
                if (tiandySDK.init(config.getSdk())) {
                    logger.info("天地伟业SDK初始化成功");
                } else {
                    logger.warn("天地伟业SDK初始化失败");
                }
            }
            
            // 初始化大华SDK
            if (config.getSdk() != null) {
                dahuaSDK = DahuaSDK.getInstance();
                if (dahuaSDK.init(config.getSdk())) {
                    logger.info("大华SDK初始化成功");
                } else {
                    logger.warn("大华SDK初始化失败");
                }
            }
            
            initialized = true;
            
        } catch (Exception e) {
            logger.error("SDK工厂初始化异常", e);
        }
    }
    
    /**
     * 根据品牌获取SDK实例
     */
    public static DeviceSDK getSDK(String brand) {
        if (brand == null || brand.isEmpty() || "auto".equalsIgnoreCase(brand)) {
            return null; // 需要自动检测
        }
        
        switch (brand.toLowerCase()) {
            case "hikvision":
                return hikvisionSDK;
            case "tiandy":
                return tiandySDK;
            case "dahua":
                return dahuaSDK;
            default:
                logger.warn("未知的品牌: {}", brand);
                return null;
        }
    }
    
    /**
     * 自动检测设备品牌
     * 依次尝试海康、天地伟业、大华SDK登录
     * 
     * @param ip 设备IP
     * @param port 设备端口
     * @param username 用户名
     * @param password 密码
     * @return 检测到的品牌和登录句柄，如果都失败返回null
     */
    public static BrandDetectionResult detectBrand(String ip, short port, String username, String password) {
        logger.info("开始自动检测设备品牌: {}:{}", ip, port);
        
        // 1. 尝试海康SDK
        if (hikvisionSDK != null) {
            logger.debug("尝试使用海康SDK登录...");
            int userId = hikvisionSDK.login(ip, port, username, password);
            if (userId != -1) {
                logger.info("设备品牌检测成功: 海康威视 (userId: {})", userId);
                return new BrandDetectionResult("hikvision", userId, hikvisionSDK);
            } else {
                logger.debug("海康SDK登录失败，错误: {}", hikvisionSDK.getLastErrorString());
            }
        }
        
        // 2. 尝试天地伟业SDK
        if (tiandySDK != null) {
            logger.debug("尝试使用天地伟业SDK登录...");
            int userId = tiandySDK.login(ip, port, username, password);
            if (userId != -1) {
                logger.info("设备品牌检测成功: 天地伟业 (logonID: {})", userId);
                return new BrandDetectionResult("tiandy", userId, tiandySDK);
            } else {
                logger.debug("天地伟业SDK登录失败，错误: {}", tiandySDK.getLastErrorString());
            }
        }
        
        // 3. 尝试大华SDK
        if (dahuaSDK != null) {
            logger.debug("尝试使用大华SDK登录...");
            int userId = dahuaSDK.login(ip, port, username, password);
            if (userId != -1) {
                logger.info("设备品牌检测成功: 大华 (userId: {})", userId);
                return new BrandDetectionResult("dahua", userId, dahuaSDK);
            } else {
                logger.debug("大华SDK登录失败，错误: {}", dahuaSDK.getLastErrorString());
            }
        }
        
        logger.warn("所有SDK登录都失败，无法检测设备品牌: {}:{}", ip, port);
        return null;
    }
    
    /**
     * 清理所有SDK资源
     */
    public static void cleanup() {
        if (hikvisionSDK != null) {
            hikvisionSDK.cleanup();
        }
        if (tiandySDK != null) {
            tiandySDK.cleanup();
        }
        if (dahuaSDK != null) {
            dahuaSDK.cleanup();
        }
        initialized = false;
        logger.info("所有SDK资源清理完成");
    }
    
    /**
     * 品牌检测结果
     */
    public static class BrandDetectionResult {
        private final String brand;
        private final int userId;
        private final DeviceSDK sdk;
        
        public BrandDetectionResult(String brand, int userId, DeviceSDK sdk) {
            this.brand = brand;
            this.userId = userId;
            this.sdk = sdk;
        }
        
        public String getBrand() {
            return brand;
        }
        
        public int getUserId() {
            return userId;
        }
        
        public DeviceSDK getSdk() {
            return sdk;
        }
    }
}
