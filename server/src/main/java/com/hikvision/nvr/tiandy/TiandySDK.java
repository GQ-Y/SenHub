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
        // 天地伟业SDK支持大于32767的端口（如37777），但short类型会溢出
        // 如果port是负数，说明发生了溢出（如37777会变成-27759）
        // 需要恢复原始值：port & 0xFFFF 会将负数转换为无符号short值，但这不是我们想要的
        // 实际上，如果port是负数，说明原始值大于32767，但无法从负数恢复原始值
        // 因此，对于天地伟业SDK，应该直接使用loginWithIntPort方法，而不是通过这个接口
        // 这里保留此方法以兼容接口，但实际应该使用loginWithIntPort
        int intPort = port < 0 ? (port & 0xFFFF) : port;
        // 如果port是负数，说明原始值在32768-65535之间，需要加上65536
        if (port < 0) {
            intPort = port & 0xFFFF; // 转换为无符号short值（0-65535）
        } else {
            intPort = port;
        }
        return loginWithIntPort(ip, intPort, username, password);
    }
    
    /**
     * 使用int类型端口登录（支持大于32767的端口，如37777）
     * 这是天地伟业SDK的特殊需求，因为其默认端口37777超过了short的最大值
     */
    public int loginWithIntPort(String ip, int port, String username, String password) {
        if (!initialized || nvssdkLibrary == null) {
            logger.error("天地伟业SDK未初始化");
            return -1;
        }
        
        try {
            // 按照天地伟业SDK示例代码的方式构造登录参数（参考Device.java第257-264行）
            String strCharSet = "UTF-8";
            
            TiandySDKStructure.tagLogonPara logonPara = new TiandySDKStructure.tagLogonPara();
            logonPara.iSize = logonPara.size();
            
            // 按照示例代码的方式，直接使用getBytes()赋值
            // 示例代码：tNormal.cNvsIP = _strDevOrPublicIP.getBytes();
            logonPara.cNvsIP = ip.getBytes();
            logonPara.iNvsPort = port;
            logonPara.cUserName = username.getBytes();
            logonPara.cUserPwd = password.getBytes();
            logonPara.cCharSet = strCharSet.getBytes();
            
            // 其他字段保持默认值（null或0）
            // cProxy, cNvsName, cProductID, cAccontName, cAccontPasswd, cNvsIPV6 使用默认值
            
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
