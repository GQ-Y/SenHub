package com.hikvision.nvr.dahua;

import com.hikvision.nvr.Common.ArchitectureChecker;
import com.hikvision.nvr.config.Config;
import com.hikvision.nvr.device.DeviceSDK;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
import com.hikvision.nvr.dahua.lib.ToolKits;
import com.hikvision.nvr.dahua.lib.LastError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.sun.jna.Native;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 大华SDK封装类
 */
public class DahuaSDK implements DeviceSDK {
    private static final Logger logger = LoggerFactory.getLogger(DahuaSDK.class);
    private static DahuaSDK instance;
    
    private NetSDKLib netsdk;
    private boolean initialized = false;
    private Config.SdkConfig sdkConfig;
    
    // 存储登录句柄：userId -> loginHandle
    private final Map<Integer, NetSDKLib.LLong> loginHandles = new ConcurrentHashMap<>();
    private int nextUserId = 1;
    
    private DahuaSDK() {
    }
    
    public static synchronized DahuaSDK getInstance() {
        if (instance == null) {
            instance = new DahuaSDK();
        }
        return instance;
    }
    
    @Override
    public boolean init(Config.SdkConfig config) {
        if (initialized) {
            return true;
        }
        
        this.sdkConfig = config;
        
        try {
            // 加载大华SDK库（使用绝对路径，避免静态初始化问题）
            if (!loadLibrary()) {
                logger.error("加载大华SDK库失败");
                return false;
            }
            
            // 调用CLIENT_Init
            boolean initResult = netsdk.CLIENT_Init(null, null);
            
            if (!initResult) {
                logger.error("大华SDK初始化失败");
                return false;
            }
            
            // 设置连接超时
            netsdk.CLIENT_SetConnectTime(10000, 3);
            
            initialized = true;
            logger.info("大华SDK初始化成功");
            return true;
            
        } catch (Exception e) {
            logger.error("大华SDK初始化异常", e);
            return false;
        }
    }
    
    /**
     * 加载SDK库
     * 使用绝对路径直接加载，避免NetSDKLib静态初始化时路径未设置的问题
     */
    private boolean loadLibrary() {
        if (netsdk != null) {
            return true;
        }
        
        try {
            // 大华SDK库文件在 ./lib/dahua/ 目录下
            String libDir = System.getProperty("user.dir") + "/lib/dahua";
            String libPath = libDir + "/libdhnetsdk.so";
            
            File libFile = new File(libPath);
            if (!libFile.exists()) {
                logger.error("大华SDK库文件不存在: {}", libPath);
                return false;
            }
            
            // 检查库文件架构是否与系统架构匹配
            if (!ArchitectureChecker.checkArchitecture(libFile)) {
                logger.warn("大华SDK库文件架构不匹配，跳过加载");
                return false;
            }
            
            // 设置库路径到LD_LIBRARY_PATH（用于加载依赖库）
            String currentLibPath = System.getProperty("java.library.path");
            String newLibPath = libDir + 
                (currentLibPath != null ? ":" + currentLibPath : "");
            System.setProperty("java.library.path", newLibPath);
            
            // 使用绝对路径直接加载NetSDKLib，而不是使用静态的NETSDK_INSTANCE
            // 这样可以避免静态初始化时路径未设置的问题
            try {
                netsdk = (NetSDKLib) Native.load(libPath, NetSDKLib.class);
                logger.info("大华SDK库加载成功，库路径: {}", libPath);
                return true;
            } catch (UnsatisfiedLinkError e) {
                logger.error("加载大华SDK库失败，库路径: {}，错误: {}", libPath, e.getMessage());
                // 如果直接加载失败，尝试使用LibraryLoad方式（作为备选）
                try {
                    com.hikvision.nvr.dahua.lib.LibraryLoad.setExtractPath(libDir);
                    String loadPath = com.hikvision.nvr.dahua.lib.LibraryLoad.getLoadLibrary("dhnetsdk");
                    netsdk = (NetSDKLib) Native.load(loadPath, NetSDKLib.class);
                    logger.info("大华SDK库加载成功（使用LibraryLoad方式），库路径: {}", loadPath);
                    return true;
                } catch (Exception e2) {
                    logger.error("使用LibraryLoad方式加载也失败: {}", e2.getMessage());
                    return false;
                }
            }
            
        } catch (Exception e) {
            logger.error("加载大华SDK库异常", e);
            return false;
        }
    }
    
    @Override
    public int login(String ip, short port, String username, String password) {
        if (!initialized || netsdk == null) {
            logger.error("大华SDK未初始化");
            return -1;
        }
        
        try {
            // 创建登录参数
            NetSDKLib.NET_IN_LOGIN_WITH_HIGHLEVEL_SECURITY pstInParam = 
                new NetSDKLib.NET_IN_LOGIN_WITH_HIGHLEVEL_SECURITY();
            pstInParam.nPort = port;
            pstInParam.szIP = ip.getBytes();
            pstInParam.szUserName = username.getBytes();
            pstInParam.szPassword = password.getBytes();
            
            // 创建输出参数
            NetSDKLib.NET_OUT_LOGIN_WITH_HIGHLEVEL_SECURITY pstOutParam = 
                new NetSDKLib.NET_OUT_LOGIN_WITH_HIGHLEVEL_SECURITY();
            
            logger.info("开始登录大华设备: {}:{}, 用户: {}", ip, port, username);
            
            // 调用登录接口
            NetSDKLib.LLong loginHandle = netsdk.CLIENT_LoginWithHighLevelSecurity(pstInParam, pstOutParam);
            
            if (loginHandle.longValue() == 0) {
                String errorMsg = ToolKits.getErrorCodePrint();
                logger.error("大华设备登录失败: {}:{}, 错误: {}", ip, port, errorMsg);
                return -1;
            }
            
            // 分配一个userId并存储登录句柄
            int userId = nextUserId++;
            loginHandles.put(userId, loginHandle);
            
            logger.info("大华设备登录成功: {}:{} (userId: {}, handle: {})", ip, port, userId, loginHandle.longValue());
            return userId;
            
        } catch (Exception e) {
            logger.error("大华设备登录异常: {}:{}", ip, port, e);
            return -1;
        }
    }
    
    @Override
    public boolean logout(int userId) {
        if (!initialized || netsdk == null) {
            return false;
        }
        
        NetSDKLib.LLong loginHandle = loginHandles.get(userId);
        if (loginHandle == null) {
            logger.warn("大华设备登录句柄不存在: {}", userId);
            return false;
        }
        
        try {
            boolean result = netsdk.CLIENT_Logout(loginHandle);
            if (result) {
                loginHandles.remove(userId);
                logger.info("大华设备登出成功: {}", userId);
            } else {
                logger.error("大华设备登出失败: {}", userId);
            }
            return result;
            
        } catch (Exception e) {
            logger.error("大华设备登出异常: {}", userId, e);
            return false;
        }
    }
    
    @Override
    public int getLastError() {
        try {
            if (netsdk != null) {
                return netsdk.CLIENT_GetLastError();
            }
            return -1;
        } catch (Exception e) {
            logger.error("获取大华SDK错误码异常", e);
            return -1;
        }
    }
    
    @Override
    public String getLastErrorString() {
        try {
            return ToolKits.getErrorCodePrint();
        } catch (Exception e) {
            int errorCode = getLastError();
            return "错误码: " + errorCode;
        }
    }
    
    @Override
    public void cleanup() {
        if (initialized && netsdk != null) {
            try {
                // 登出所有设备
                for (Integer userId : loginHandles.keySet()) {
                    logout(userId);
                }
                
                // 调用CLIENT_Cleanup
                netsdk.CLIENT_Cleanup();
                
                initialized = false;
                logger.info("大华SDK清理完成");
                
            } catch (Exception e) {
                logger.error("大华SDK清理异常", e);
            }
        }
    }
    
    @Override
    public String getBrand() {
        return "dahua";
    }
}
