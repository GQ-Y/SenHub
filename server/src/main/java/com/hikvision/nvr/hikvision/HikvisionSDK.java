package com.hikvision.nvr.hikvision;

import com.hikvision.nvr.Common.ArchitectureChecker;
import com.hikvision.nvr.Common.osSelect;
import com.hikvision.nvr.config.Config;
import com.hikvision.nvr.device.DeviceSDK;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * 海康威视SDK封装类
 */
public class HikvisionSDK implements DeviceSDK {
    private static final Logger logger = LoggerFactory.getLogger(HikvisionSDK.class);
    private static HikvisionSDK instance;
    private HCNetSDK hCNetSDK;
    private boolean initialized = false;
    private Config.SdkConfig sdkConfig;

    private HikvisionSDK() {
    }

    public static synchronized HikvisionSDK getInstance() {
        if (instance == null) {
            instance = new HikvisionSDK();
        }
        return instance;
    }

    /**
     * 初始化SDK
     */
    public boolean init() {
        return init(null);
    }

    /**
     * 初始化SDK
     */
    @Override
    public boolean init(Config.SdkConfig config) {
        if (initialized) {
            return true;
        }

        this.sdkConfig = config;

        try {
            // 加载SDK库
            if (!loadLibrary()) {
                logger.error("加载SDK库失败");
                return false;
            }

            // Linux系统需要设置库路径
            if (osSelect.isLinux() && config != null) {
                setupLinuxLibraries(config);
            }

            // SDK初始化
            if (!hCNetSDK.NET_DVR_Init()) {
                logger.error("SDK初始化失败，错误码: {}", getLastError());
                return false;
            }

            // 设置异常回调
            FExceptionCallBack_Imp exceptionCallback = new FExceptionCallBack_Imp();
            if (!hCNetSDK.NET_DVR_SetExceptionCallBack_V30(0, 0, exceptionCallback, null)) {
                logger.warn("设置异常回调失败");
            }

            // 设置日志
            if (config != null && config.getLogPath() != null) {
                hCNetSDK.NET_DVR_SetLogToFile(
                    config.getLogLevel(),
                    config.getLogPath(),
                    false
                );
            }

            initialized = true;
            logger.info("SDK初始化成功");
            return true;

        } catch (Exception e) {
            logger.error("SDK初始化异常", e);
            return false;
        }
    }

    /**
     * 加载SDK库
     */
    private boolean loadLibrary() {
        if (hCNetSDK != null) {
            return true;
        }

        try {
            String libPath;
            if (sdkConfig != null && sdkConfig.getLibPath() != null) {
                libPath = sdkConfig.getLibPath() + "/libhcnetsdk.so";
            } else {
                libPath = System.getProperty("user.dir") + "/lib/hikvision/libhcnetsdk.so";
            }

            File libFile = new File(libPath);
            if (!libFile.exists()) {
                logger.error("SDK库文件不存在: {}", libPath);
                return false;
            }
            
            // 检查库文件架构是否与系统架构匹配
            if (!ArchitectureChecker.checkArchitecture(libFile)) {
                logger.warn("海康SDK库文件架构不匹配，跳过加载");
                return false;
            }

            hCNetSDK = (HCNetSDK) Native.loadLibrary(libPath, HCNetSDK.class);
            logger.info("SDK库加载成功: {}", libPath);
            return true;

        } catch (Exception e) {
            logger.error("加载SDK库异常", e);
            return false;
        }
    }

    /**
     * 设置Linux系统库路径
     */
    private void setupLinuxLibraries(Config.SdkConfig config) {
        try {
            String libPath = config.getLibPath();
            if (libPath == null) {
                libPath = System.getProperty("user.dir") + "/lib/hikvision";
            }

            // 设置crypto和ssl库路径
            String cryptoPath = libPath + "/libcrypto.so.1.1";
            String sslPath = libPath + "/libssl.so.1.1";

            HCNetSDK.BYTE_ARRAY ptrByteArray1 = new HCNetSDK.BYTE_ARRAY(256);
            HCNetSDK.BYTE_ARRAY ptrByteArray2 = new HCNetSDK.BYTE_ARRAY(256);

            System.arraycopy(cryptoPath.getBytes(), 0, ptrByteArray1.byValue, 0, Math.min(cryptoPath.length(), 255));
            ptrByteArray1.write();
            hCNetSDK.NET_DVR_SetSDKInitCfg(HCNetSDK.NET_SDK_INIT_CFG_LIBEAY_PATH, ptrByteArray1.getPointer());

            System.arraycopy(sslPath.getBytes(), 0, ptrByteArray2.byValue, 0, Math.min(sslPath.length(), 255));
            ptrByteArray2.write();
            hCNetSDK.NET_DVR_SetSDKInitCfg(HCNetSDK.NET_SDK_INIT_CFG_SSLEAY_PATH, ptrByteArray2.getPointer());

            // 设置组件库路径
            String strPathCom = libPath + "/HCNetSDKCom";
            HCNetSDK.NET_DVR_LOCAL_SDK_PATH struComPath = new HCNetSDK.NET_DVR_LOCAL_SDK_PATH();
            System.arraycopy(strPathCom.getBytes(), 0, struComPath.sPath, 0, Math.min(strPathCom.length(), 255));
            struComPath.write();
            hCNetSDK.NET_DVR_SetSDKInitCfg(HCNetSDK.NET_SDK_INIT_CFG_SDK_PATH, struComPath.getPointer());

            logger.info("Linux库路径设置成功");

        } catch (Exception e) {
            logger.error("设置Linux库路径异常", e);
        }
    }

    /**
     * 清理SDK资源
     */
    @Override
    public void cleanup() {
        if (initialized && hCNetSDK != null) {
            hCNetSDK.NET_DVR_Cleanup();
            initialized = false;
            logger.info("SDK清理完成");
        }
    }

    /**
     * 获取最后错误码
     */
    @Override
    public int getLastError() {
        if (hCNetSDK == null) {
            return -1;
        }
        return hCNetSDK.NET_DVR_GetLastError();
    }
    
    /**
     * 获取最后错误信息（字符串描述）
     */
    @Override
    public String getLastErrorString() {
        int errorCode = getLastError();
        if (errorCode == 0) {
            return "没有错误";
        }
        switch (errorCode) {
            case HCNetSDK.NET_DVR_PASSWORD_ERROR:
                return "用户名或密码错误";
            case HCNetSDK.NET_DVR_NOENOUGHPRI:
                return "权限不足";
            case HCNetSDK.NET_DVR_NOINIT:
                return "没有初始化";
            case HCNetSDK.NET_DVR_CHANNEL_ERROR:
                return "通道号错误";
            case HCNetSDK.NET_DVR_OVER_MAXLINK:
                return "连接到DVR的客户端个数超过最大";
            case HCNetSDK.NET_DVR_VERSIONNOMATCH:
                return "版本不匹配";
            case HCNetSDK.NET_DVR_NETWORK_FAIL_CONNECT:
                return "连接服务器失败";
            case HCNetSDK.NET_DVR_NETWORK_SEND_ERROR:
                return "向服务器发送失败";
            case HCNetSDK.NET_DVR_NETWORK_RECV_ERROR:
                return "从服务器接收数据失败";
            case HCNetSDK.NET_DVR_NETWORK_RECV_TIMEOUT:
                return "从服务器接收数据超时";
            default:
                return "未知错误，错误码: " + errorCode;
        }
    }
    
    /**
     * 获取品牌名称
     */
    @Override
    public String getBrand() {
        return "hikvision";
    }

    /**
     * 登录设备
     */
    @Override
    public int login(String ip, short port, String username, String password) {
        if (!initialized || hCNetSDK == null) {
            logger.error("SDK未初始化");
            return -1;
        }

        HCNetSDK.NET_DVR_USER_LOGIN_INFO loginInfo = new HCNetSDK.NET_DVR_USER_LOGIN_INFO();
        HCNetSDK.NET_DVR_DEVICEINFO_V40 deviceInfo = new HCNetSDK.NET_DVR_DEVICEINFO_V40();

        // 设置设备IP地址（按照示例代码的方式）
        byte[] deviceAddress = new byte[HCNetSDK.NET_DVR_DEV_ADDRESS_MAX_LEN];
        byte[] ipBytes = ip.getBytes();
        System.arraycopy(ipBytes, 0, deviceAddress, 0, Math.min(ipBytes.length, deviceAddress.length));
        loginInfo.sDeviceAddress = deviceAddress;

        // 设置用户名（按照示例代码的方式）
        byte[] userName = new byte[HCNetSDK.NET_DVR_LOGIN_USERNAME_MAX_LEN];
        byte[] userBytes = username.getBytes();
        System.arraycopy(userBytes, 0, userName, 0, Math.min(userBytes.length, userName.length));
        loginInfo.sUserName = userName;

        // 设置密码（按照示例代码的方式）
        byte[] passwordBytes = password.getBytes();
        System.arraycopy(passwordBytes, 0, loginInfo.sPassword, 0, Math.min(passwordBytes.length, loginInfo.sPassword.length));

        // 设置端口和登录模式
        loginInfo.wPort = port;
        loginInfo.bUseAsynLogin = false; // 同步登录
        loginInfo.byLoginMode = 0; // 使用SDK私有协议
        loginInfo.byUseTransport = 0; // 不使用传输层协议
        loginInfo.byProxyType = 0; // 不使用代理
        loginInfo.byHttps = 0; // 不使用HTTPS
        
        // 设置连接超时（可选，默认可能较短）
        // NET_DVR_SetConnectTime(等待时间ms, 重试次数)
        hCNetSDK.NET_DVR_SetConnectTime(10000, 3); // 等待10秒，重试3次

        // 确保结构体数据同步到本地内存（JNA需要）
        loginInfo.write();
        deviceInfo.write();

        // 执行登录
        logger.info("开始登录设备: {}:{}, 用户: {}", ip, port, username);
        logger.debug("登录参数详情: IP={}, Port={}, Username={}, Password长度={}, LoginMode={}", 
            ip, port, username, password != null ? password.length() : 0, loginInfo.byLoginMode);
        
        int userID = hCNetSDK.NET_DVR_Login_V40(loginInfo, deviceInfo);
        
        // 读取设备信息（登录成功后设备信息会被填充）
        deviceInfo.read();
        
        logger.debug("登录调用返回: userID={}", userID);
        
        // 检查设备信息（登录成功后设备信息会被填充）
        if (deviceInfo.struDeviceV30 != null) {
            int chanNum = deviceInfo.struDeviceV30.byChanNum & 0xFF; // 转换为无符号
            int startDChan = deviceInfo.struDeviceV30.byStartDChan & 0xFF;
            logger.debug("设备信息: 通道数={}, 起始通道={}", chanNum, startDChan);
        }
        
        // 按照示例代码的逻辑：只要 userID != -1 就认为登录成功（包括 userID=0）
        if (userID == -1) {
            int errorCode = hCNetSDK.NET_DVR_GetLastError();
            logger.error("登录失败，错误码: {} (IP: {}:{}, 用户: {})", errorCode, ip, port, username);
            
            // 输出常见错误码的含义
            if (errorCode == HCNetSDK.NET_DVR_PASSWORD_ERROR) {
                logger.error("错误原因: 用户名或密码错误");
            } else if (errorCode == HCNetSDK.NET_DVR_NETWORK_FAIL_CONNECT) {
                logger.error("错误原因: 连接服务器失败 (错误码: 7)");
                logger.error("可能原因: 1)设备不在线或网络不通 2)端口错误 3)防火墙阻止连接");
                logger.error("         4)设备不是海康品牌，无法使用海康SDK连接 (如天地伟业、大华等品牌需要使用对应的SDK)");
                logger.error("         5)设备不支持海康SDK的私有协议");
            } else if (errorCode == HCNetSDK.NET_DVR_NETWORK_SEND_ERROR || errorCode == HCNetSDK.NET_DVR_NETWORK_RECV_ERROR) {
                logger.error("错误原因: 网络通信错误，请检查网络连接和端口");
            } else if (errorCode == HCNetSDK.NET_DVR_NETWORK_RECV_TIMEOUT) {
                logger.error("错误原因: 网络接收超时，设备可能未响应");
            } else if (errorCode == 0) {
                logger.warn("注意: 错误码为0，可能是设备未响应或连接超时");
                logger.warn("可能原因: 1)设备不在线 2)端口错误 3)防火墙阻止 4)设备不支持该登录方式");
            } else {
                logger.warn("其他错误，错误码: {}", errorCode);
            }
        } else {
            logger.info("设备登录成功: {}:{} (userId: {})", ip, port, userID);
        }

        return userID;
    }

    /**
     * 登出设备
     */
    @Override
    public boolean logout(int userID) {
        if (!initialized || hCNetSDK == null) {
            return false;
        }
        boolean result = hCNetSDK.NET_DVR_Logout_V30(userID);
        if (result) {
            logger.info("设备登出成功: {}", userID);
        } else {
            logger.error("设备登出失败，错误码: {}", getLastError());
        }
        return result;
    }

    /**
     * 获取SDK接口
     */
    public HCNetSDK getSDK() {
        return hCNetSDK;
    }

    /**
     * 异常回调实现
     */
    static class FExceptionCallBack_Imp implements HCNetSDK.FExceptionCallBack {
        private static final Logger logger = LoggerFactory.getLogger(FExceptionCallBack_Imp.class);

        @Override
        public void invoke(int dwType, int lUserID, int lHandle, Pointer pUser) {
            logger.warn("SDK异常事件 - 类型: {}, 用户ID: {}, 句柄: {}", dwType, lUserID, lHandle);
        }
    }
}
