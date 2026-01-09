package com.digital.video.gateway.device;

import com.digital.video.gateway.Common.LibraryPathHelper;
import com.digital.video.gateway.config.Config;
import com.digital.video.gateway.dahua.DahuaSDK;
import com.digital.video.gateway.hikvision.HikvisionSDK;
import com.digital.video.gateway.tiandy.TiandySDK;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

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
     * 不管是否有设备，只要库文件存在且架构匹配，就尝试初始化
     */
    public static void init(Config config) {
        if (initialized) {
            return;
        }
        
        try {
            // 创建默认SDK配置（如果config.getSdk()为null）
            Config.SdkConfig sdkConfig = config.getSdk();
            if (sdkConfig == null) {
                sdkConfig = new Config.SdkConfig();
                sdkConfig.setLibPath("./lib/hikvision");
                sdkConfig.setLogPath("./sdkLog");
                sdkConfig.setLogLevel(3);
                logger.info("使用默认SDK配置");
            }
            
            // 预先设置大华SDK库路径，避免NetSDKLib静态初始化时找不到库
            // 必须在任何SDK初始化之前设置，因为NetSDKLib.NETSDK_INSTANCE是静态的
            try {
                String libDir = LibraryPathHelper.getSDKLibPath("dahua");
                if (libDir != null) {
                    java.io.File libDirFile = new java.io.File(libDir);
                    if (libDirFile.exists()) {
                        com.digital.video.gateway.dahua.lib.LibraryLoad.setExtractPath(libDir);
                        logger.debug("大华SDK库路径已设置: {}", libDir);
                    }
                }
            } catch (Exception e) {
                logger.debug("设置大华SDK库路径失败: {}", e.getMessage());
            }
            
            // 初始化海康SDK（不依赖config.getSdk()，SDK内部会检查库文件和架构）
            logger.info("尝试初始化海康SDK...");
            hikvisionSDK = HikvisionSDK.getInstance();
            if (hikvisionSDK.init(sdkConfig)) {
                logger.info("✓ 海康SDK初始化成功");
            } else {
                logger.warn("✗ 海康SDK初始化失败（可能原因：库文件不存在或架构不匹配）");
            }
            
            // 初始化天地伟业SDK（不依赖config.getSdk()，SDK内部会检查库文件和架构）
            // 注意：天地伟业仅支持x86架构，在ARM系统上会自动跳过
            logger.info("尝试初始化天地伟业SDK...");
            String archDir = LibraryPathHelper.getArchitectureDir();
            if ("arm".equals(archDir)) {
                logger.info("✗ 天地伟业SDK仅支持x86架构，当前系统为ARM，跳过初始化");
            } else {
                tiandySDK = TiandySDK.getInstance();
                if (tiandySDK.init(sdkConfig)) {
                    logger.info("✓ 天地伟业SDK初始化成功");
                } else {
                    logger.warn("✗ 天地伟业SDK初始化失败（可能原因：库文件不存在或架构不匹配）");
                }
            }
            
            // 初始化大华SDK（不依赖config.getSdk()，SDK内部会检查库文件和架构）
            logger.info("尝试初始化大华SDK...");
            dahuaSDK = DahuaSDK.getInstance();
            if (dahuaSDK.init(sdkConfig)) {
                logger.info("✓ 大华SDK初始化成功");
            } else {
                logger.warn("✗ 大华SDK初始化失败（可能原因：库文件不存在或架构不匹配）");
            }
            
            initialized = true;
            logger.info("SDK工厂初始化完成（部分SDK可能未初始化，这是正常的）");
            
        } catch (Exception e) {
            logger.error("SDK工厂初始化异常", e);
        }
    }
    
    /**
     * 根据品牌获取SDK实例
     * 只返回已初始化的SDK实例，未初始化的SDK返回null
     */
    public static DeviceSDK getSDK(String brand) {
        if (brand == null || brand.isEmpty() || "auto".equalsIgnoreCase(brand)) {
            return null; // 需要自动检测
        }
        
        DeviceSDK sdk = null;
        switch (brand.toLowerCase()) {
            case "hikvision":
                sdk = hikvisionSDK;
                break;
            case "tiandy":
                sdk = tiandySDK;
                break;
            case "dahua":
                sdk = dahuaSDK;
                break;
            default:
                logger.warn("未知的品牌: {}", brand);
                return null;
        }
        
        // 检查SDK是否已初始化
        if (sdk != null && sdk.isInitialized()) {
            return sdk;
        } else if (sdk != null) {
            logger.warn("SDK未初始化: {} (可能原因：库文件不存在、架构不匹配或初始化失败)", brand);
            return null;
        } else {
            logger.warn("SDK实例不存在: {} (可能原因：库文件不存在或架构不匹配)", brand);
            return null;
        }
    }
    
    /**
     * 检查指定品牌的SDK是否已初始化
     */
    public static boolean isSDKInitialized(String brand) {
        DeviceSDK sdk = getSDK(brand);
        return sdk != null && sdk.isInitialized();
    }
    
    /**
     * 获取所有已初始化的SDK状态信息
     */
    public static Map<String, Boolean> getAllSDKStatus() {
        Map<String, Boolean> status = new java.util.HashMap<>();
        status.put("hikvision", hikvisionSDK != null && hikvisionSDK.isInitialized());
        status.put("tiandy", tiandySDK != null && tiandySDK.isInitialized());
        status.put("dahua", dahuaSDK != null && dahuaSDK.isInitialized());
        return status;
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
