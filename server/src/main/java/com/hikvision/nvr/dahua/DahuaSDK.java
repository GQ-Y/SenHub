package com.hikvision.nvr.dahua;

import com.hikvision.nvr.config.Config;
import com.hikvision.nvr.device.DeviceSDK;
import com.hikvision.nvr.dahua.lib.NetSDKLib;
import com.hikvision.nvr.dahua.lib.ToolKits;
import com.hikvision.nvr.dahua.lib.LastError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
            // 加载大华SDK库
            if (!loadLibrary()) {
                logger.error("加载大华SDK库失败");
                return false;
            }
            
            // 获取NetSDK实例
            netsdk = NetSDKLib.NETSDK_INSTANCE;
            
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
     */
    private boolean loadLibrary() {
        try {
            String libPath;
            if (sdkConfig != null && sdkConfig.getLibPath() != null) {
                libPath = sdkConfig.getLibPath() + "/libdhnetsdk.so";
            } else {
                libPath = System.getProperty("user.dir") + "/lib/dahua/libdhnetsdk.so";
            }
            
            File libFile = new File(libPath);
            if (!libFile.exists()) {
                logger.error("大华SDK库文件不存在: {}", libPath);
                return false;
            }
            
            // 设置库路径
            String currentLibPath = System.getProperty("java.library.path");
            String newLibPath = libFile.getParent() + 
                (currentLibPath != null ? ":" + currentLibPath : "");
            System.setProperty("java.library.path", newLibPath);
            
            logger.info("大华SDK库路径设置成功: {}", libPath);
            return true;
            
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
