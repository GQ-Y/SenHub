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
    /** 海康延迟初始化：仅当首次 getSDK("hikvision") 时再初始化，避免启动早期崩溃 */
    private static Config.SdkConfig sdkConfigForLazyInit;
    private static boolean hikvisionAvailable;

    /**
     * 初始化所有SDK（海康采用延迟初始化，仅在首次使用时初始化）
     */
    public static void init(Config config) {
        if (initialized) {
            return;
        }

        try {
            Config.SdkConfig sdkConfig = config.getSdk();
            if (sdkConfig == null) {
                sdkConfig = new Config.SdkConfig();
                sdkConfig.setLibPath("./lib/hikvision");
                sdkConfig.setLogPath("./sdkLog");
                sdkConfig.setLogLevel(3);
                logger.info("使用默认SDK配置");
            }

            Map<String, Boolean> sdkAvailability = detectAvailableSDKs(sdkConfig);
            sdkConfigForLazyInit = sdkConfig;
            hikvisionAvailable = Boolean.TRUE.equals(sdkAvailability.get("hikvision"));

            prepareDahuaLibPath(sdkConfig);

            if (hikvisionAvailable) {
                logger.info("海康SDK库已检测到，将在首次使用时初始化（延迟初始化）");
            } else {
                logger.info("跳过海康SDK：未检测到可用库文件");
            }

            // 初始化天地伟业SDK（仅x86且检测到库后）
            if (Boolean.TRUE.equals(sdkAvailability.get("tiandy"))) {
                logger.info("尝试初始化天地伟业SDK...");
                tiandySDK = TiandySDK.getInstance();
                if (tiandySDK.init(sdkConfig)) {
                    logger.info("✓ 天地伟业SDK初始化成功");
                } else {
                    logger.warn("✗ 天地伟业SDK初始化失败（可能原因：库文件不存在或架构不匹配）");
                }
            } else {
                logger.info("跳过天地伟业SDK初始化：未检测到可用库文件或架构不支持");
            }
            
            // 初始化大华SDK（检测到库后再初始化）
            if (Boolean.TRUE.equals(sdkAvailability.get("dahua"))) {
                logger.info("尝试初始化大华SDK...");
                dahuaSDK = DahuaSDK.getInstance();
                if (dahuaSDK.init(sdkConfig)) {
                    logger.info("✓ 大华SDK初始化成功");
                } else {
                    logger.warn("✗ 大华SDK初始化失败（可能原因：库文件不存在或架构不匹配）");
                }
            } else {
                logger.info("跳过大华SDK初始化：未检测到可用库文件");
            }
            
            initialized = true;
            logger.info("SDK工厂初始化完成（部分SDK可能未初始化，这是正常的）");
            
        } catch (Exception e) {
            logger.error("SDK工厂初始化异常", e);
        }
    }

    /**
     * 检测各品牌SDK库文件是否存在并与架构匹配
     */
    private static Map<String, Boolean> detectAvailableSDKs(Config.SdkConfig sdkConfig) {
        java.util.Map<String, Boolean> result = new java.util.HashMap<>();
        String archDir = LibraryPathHelper.getArchitectureDir();

        // 海康：arm/x86 均支持
        result.put("hikvision", checkLibExists(sdkConfig, "hikvision"));

        // 天地伟业：仅x86
        if ("arm".equals(archDir)) {
            result.put("tiandy", false);
        } else {
            result.put("tiandy", checkLibExists(sdkConfig, "tiandy"));
        }

        // 大华：arm/x86 均支持
        result.put("dahua", checkLibExists(sdkConfig, "dahua"));

        logger.info("SDK可用性检测结果: {}", result);
        return result;
    }

    private static boolean checkLibExists(Config.SdkConfig sdkConfig, String sdkName) {
        try {
            String libDir = LibraryPathHelper.getSDKLibPath(
                    sdkConfig != null ? sdkConfig.getLibPath() : null,
                    sdkName);
            if (libDir == null) {
                return false;
            }
            java.io.File dir = new java.io.File(libDir);
            boolean exists = dir.exists() && dir.isDirectory();
            if (!exists && logger.isDebugEnabled()) {
                logger.debug("未找到SDK库目录: {}", libDir);
            }
            return exists;
        } catch (Exception e) {
            logger.debug("检测SDK库目录异常: {}", e.getMessage());
            return false;
        }
    }

    private static void prepareDahuaLibPath(Config.SdkConfig sdkConfig) {
        try {
            String libDir = LibraryPathHelper.getSDKLibPath(
                    sdkConfig != null ? sdkConfig.getLibPath() : null,
                    "dahua");
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
    }
    
    /**
     * 根据品牌获取SDK实例；海康在首次请求时延迟初始化
     */
    public static DeviceSDK getSDK(String brand) {
        if (brand == null || brand.isEmpty() || "auto".equalsIgnoreCase(brand)) {
            return null;
        }

        if ("hikvision".equalsIgnoreCase(brand)) {
            if (hikvisionSDK == null && hikvisionAvailable && sdkConfigForLazyInit != null) {
                synchronized (SDKFactory.class) {
                    if (hikvisionSDK == null) {
                        logger.info("首次使用海康SDK，执行延迟初始化...");
                        hikvisionSDK = HikvisionSDK.getInstance();
                        if (hikvisionSDK.init(sdkConfigForLazyInit)) {
                            logger.info("✓ 海康SDK延迟初始化成功");
                        } else {
                            logger.warn("✗ 海康SDK延迟初始化失败");
                            hikvisionSDK = null;
                        }
                    }
                }
            }
            return hikvisionSDK != null && hikvisionSDK.isInitialized() ? hikvisionSDK : null;
        }

        DeviceSDK sdk = null;
        switch (brand.toLowerCase()) {
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
     * SDK 健康探针：验证指定品牌 SDK 的初始化状态与基本可用性。
     * 用于 Keeper 周期中先检查 SDK 再检查设备。
     *
     * @param brand 品牌：hikvision / tiandy / dahua
     * @return 该品牌 SDK 是否健康可用
     */
    public static boolean checkSDKHealth(String brand) {
        if (brand == null || brand.isEmpty()) {
            return false;
        }
        switch (brand.toLowerCase()) {
            case "tiandy":
                return tiandySDK != null && tiandySDK.isInitialized();
            case "hikvision":
                DeviceSDK hik = getSDK("hikvision");
                if (hik == null) {
                    return false;
                }
                if (hik instanceof HikvisionSDK) {
                    return ((HikvisionSDK) hik).isExecutorAlive();
                }
                return hik.isInitialized();
            case "dahua":
                return dahuaSDK != null && dahuaSDK.isInitialized();
            default:
                return false;
        }
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
     * 自动检测设备品牌（int端口版）
     * 默认顺序：海康 -> 天地伟业 -> 大华；
     * 对高端口（>32767）场景优先尝试大华，再尝试天地伟业(int端口)。
     */
    public static BrandDetectionResult detectBrand(String ip, int port, String username, String password) {
        logger.info("开始自动检测设备品牌: {}:{}", ip, port);
        short shortPort = (short) port;
        boolean highPort = port > 32767 || port < -32768;

        if (highPort) {
            BrandDetectionResult dahuaResult = tryDahua(ip, shortPort, username, password);
            if (dahuaResult != null) {
                return dahuaResult;
            }
            BrandDetectionResult tiandyResult = tryTiandy(ip, port, username, password);
            if (tiandyResult != null) {
                return tiandyResult;
            }
            BrandDetectionResult hikResult = tryHikvision(ip, shortPort, username, password);
            if (hikResult != null) {
                return hikResult;
            }
        } else {
            BrandDetectionResult hikResult = tryHikvision(ip, shortPort, username, password);
            if (hikResult != null) {
                return hikResult;
            }
            BrandDetectionResult tiandyResult = tryTiandy(ip, port, username, password);
            if (tiandyResult != null) {
                return tiandyResult;
            }
            BrandDetectionResult dahuaResult = tryDahua(ip, shortPort, username, password);
            if (dahuaResult != null) {
                return dahuaResult;
            }
        }

        logger.warn("所有SDK登录都失败，无法检测设备品牌: {}:{}", ip, port);
        return null;
    }

    /**
     * 兼容旧调用（short端口）
     */
    public static BrandDetectionResult detectBrand(String ip, short port, String username, String password) {
        return detectBrand(ip, (int) port, username, password);
    }

    private static BrandDetectionResult tryHikvision(String ip, short port, String username, String password) {
        if (hikvisionSDK == null) {
            return null;
        }
        logger.debug("尝试使用海康SDK登录...");
        int userId = hikvisionSDK.login(ip, port, username, password);
        if (userId != -1) {
            logger.info("设备品牌检测成功: 海康威视 (userId: {})", userId);
            return new BrandDetectionResult("hikvision", userId, hikvisionSDK);
        }
        logger.debug("海康SDK登录失败，错误: {}", hikvisionSDK.getLastErrorString());
        return null;
    }

    private static BrandDetectionResult tryTiandy(String ip, int port, String username, String password) {
        if (tiandySDK == null) {
            return null;
        }
        logger.debug("尝试使用天地伟业SDK登录(int端口)...");
        int userId = tiandySDK.loginWithIntPort(ip, port, username, password);
        if (userId != -1) {
            logger.info("设备品牌检测成功: 天地伟业 (logonID: {})", userId);
            return new BrandDetectionResult("tiandy", userId, tiandySDK);
        }
        logger.debug("天地伟业SDK登录失败，错误: {}", tiandySDK.getLastErrorString());
        return null;
    }

    private static BrandDetectionResult tryDahua(String ip, short port, String username, String password) {
        if (dahuaSDK == null) {
            return null;
        }
        logger.debug("尝试使用大华SDK登录...");
        int userId = dahuaSDK.login(ip, port, username, password);
        if (userId != -1) {
            logger.info("设备品牌检测成功: 大华 (userId: {})", userId);
            return new BrandDetectionResult("dahua", userId, dahuaSDK);
        }
        logger.debug("大华SDK登录失败，错误: {}", dahuaSDK.getLastErrorString());
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
