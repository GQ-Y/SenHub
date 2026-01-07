package com.hikvision.nvr.tiandy;

import com.hikvision.nvr.Common.ArchitectureChecker;
import com.hikvision.nvr.config.Config;
import com.hikvision.nvr.device.DeviceSDK;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.sun.jna.Native;

import java.io.File;

/**
 * 天地伟业SDK封装类
 */
public class TiandySDK implements DeviceSDK {
    private static final Logger logger = LoggerFactory.getLogger(TiandySDK.class);
    private static TiandySDK instance;
    private NvssdkLibrary nvssdkLibrary;
    private boolean initialized = false;
    private Config.SdkConfig sdkConfig;
    
    private TiandySDK() {
    }
    
    public static synchronized TiandySDK getInstance() {
        if (instance == null) {
            instance = new TiandySDK();
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
            // 加载SDK库
            if (!loadLibrary()) {
                logger.error("加载天地伟业SDK库失败");
                return false;
            }
            
            // SDK初始化
            int ret = nvssdkLibrary.NetClient_Startup_V4(0, 0, 0);
            if (ret != NvssdkLibrary.RET_SUCCESS) {
                logger.error("天地伟业SDK初始化失败，返回值: {}", ret);
                return false;
            }
            
            // 设置回调函数（可以传null，简化处理）
            nvssdkLibrary.NetClient_SetNotifyFunction_V4(null, null, null, null, null);
            
            initialized = true;
            logger.info("天地伟业SDK初始化成功");
            return true;
            
        } catch (Exception e) {
            logger.error("天地伟业SDK初始化异常", e);
            return false;
        }
    }
    
    /**
     * 加载SDK库
     */
    private boolean loadLibrary() {
        if (nvssdkLibrary != null) {
            return true;
        }
        
        try {
            // 天地伟业SDK库文件在 ./lib/tiandy/ 目录下，不依赖config中的lib_path
            String libDir = System.getProperty("user.dir") + "/lib/tiandy";
            
            File libDirFile = new File(libDir);
            if (!libDirFile.exists()) {
                logger.error("天地伟业SDK库目录不存在: {}", libDir);
                return false;
            }
            
            // 查找实际的库文件
            String libFileName = "libnvssdk.so";
            File libFile = new File(libDir, libFileName);
            
            if (!libFile.exists()) {
                logger.error("天地伟业SDK库文件不存在: {}", libFile.getAbsolutePath());
                return false;
            }
            
            // 检查库文件架构是否与系统架构匹配
            if (!ArchitectureChecker.checkArchitecture(libFile)) {
                logger.warn("天地伟业SDK库文件架构不匹配，跳过加载");
                return false;
            }
            
            String actualLibPath = libFile.getAbsolutePath();
            
            // 设置库路径到LD_LIBRARY_PATH（用于加载依赖库）
            String currentLibPath = System.getProperty("java.library.path");
            String newLibPath = libDir + (currentLibPath != null ? ":" + currentLibPath : "");
            System.setProperty("java.library.path", newLibPath);
            
            // 使用绝对路径加载库（类似海康SDK的方式）
            try {
                nvssdkLibrary = (NvssdkLibrary) Native.loadLibrary(actualLibPath, NvssdkLibrary.class);
                logger.info("天地伟业SDK库加载成功，库路径: {}", actualLibPath);
                return true;
            } catch (UnsatisfiedLinkError e) {
                logger.error("加载天地伟业SDK库失败，库路径: {}，错误: {}", actualLibPath, e.getMessage());
                return false;
            }
            
        } catch (Exception e) {
            logger.error("加载天地伟业SDK库异常", e);
            return false;
        }
    }
    
    @Override
    public int login(String ip, short port, String username, String password) {
        if (!initialized || nvssdkLibrary == null) {
            logger.error("天地伟业SDK未初始化");
            return -1;
        }
        
        try {
            TiandySDKStructure.tagLogonPara logonPara = new TiandySDKStructure.tagLogonPara();
            logonPara.iSize = logonPara.size();
            System.arraycopy(ip.getBytes(), 0, logonPara.cNvsIP, 0, Math.min(ip.getBytes().length, logonPara.cNvsIP.length - 1));
            logonPara.iNvsPort = port;
            System.arraycopy(username.getBytes(), 0, logonPara.cUserName, 0, Math.min(username.getBytes().length, logonPara.cUserName.length - 1));
            System.arraycopy(password.getBytes(), 0, logonPara.cUserPwd, 0, Math.min(password.getBytes().length, logonPara.cUserPwd.length - 1));
            System.arraycopy("UTF-8".getBytes(), 0, logonPara.cCharSet, 0, Math.min("UTF-8".getBytes().length, logonPara.cCharSet.length - 1));
            logonPara.write();
            
            logger.info("开始登录天地伟业设备: {}:{}, 用户: {}", ip, port, username);
            
            int logonID = nvssdkLibrary.NetClient_SyncLogon(NvssdkLibrary.SERVER_NORMAL, logonPara.getPointer(), logonPara.iSize);
            
            if (logonID >= 0) {
                logger.info("天地伟业设备登录成功: {}:{} (logonID: {})", ip, port, logonID);
                return logonID;
            } else {
                logger.error("天地伟业设备登录失败: {}:{} (错误码: {})", ip, port, logonID);
                return -1;
            }
            
        } catch (Exception e) {
            logger.error("天地伟业设备登录异常: {}:{}", ip, port, e);
            return -1;
        }
    }
    
    @Override
    public boolean logout(int userId) {
        if (!initialized || nvssdkLibrary == null) {
            return false;
        }
        
        if (userId < 0) {
            return false;
        }
        
        int ret = nvssdkLibrary.NetClient_Logoff(userId);
        if (ret == NvssdkLibrary.RET_SUCCESS) {
            logger.info("天地伟业设备登出成功: {}", userId);
            return true;
        } else {
            logger.error("天地伟业设备登出失败: {}, 错误码: {}", userId, ret);
            return false;
        }
    }
    
    @Override
    public int getLastError() {
        if (nvssdkLibrary == null) {
            return -1;
        }
        return nvssdkLibrary.NetClient_GetLastError();
    }
    
    @Override
    public String getLastErrorString() {
        int errorCode = getLastError();
        if (errorCode == 0) {
            return "没有错误";
        }
        
        // 根据同步登录的错误码返回描述
        switch (errorCode) {
            case NvssdkLibrary.RET_SYNCLOGON_TIMEOUT:
                return "登录超时";
            case NvssdkLibrary.RET_SYNCLOGON_USENAME_ERROR:
                return "用户名错误";
            case NvssdkLibrary.RET_SYNCLOGON_USRPWD_ERROR:
                return "密码错误";
            case NvssdkLibrary.RET_SYNCLOGON_PWDERRTIMES_OVERRUN:
                return "密码错误次数超限";
            case NvssdkLibrary.RET_SYNCLOGON_NET_ERROR:
                return "网络错误";
            case NvssdkLibrary.RET_SYNCLOGON_PORT_ERROR:
                return "端口错误";
            case NvssdkLibrary.RET_SYNCLOGON_UNKNOW_ERROR:
                return "未知错误";
            default:
                return "错误码: " + errorCode;
        }
    }
    
    @Override
    public void cleanup() {
        if (initialized && nvssdkLibrary != null) {
            nvssdkLibrary.NetClient_Cleanup();
            initialized = false;
            logger.info("天地伟业SDK清理完成");
        }
    }
    
    @Override
    public String getBrand() {
        return "tiandy";
    }
}
