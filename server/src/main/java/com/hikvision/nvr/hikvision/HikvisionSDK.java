package com.hikvision.nvr.hikvision;

import com.hikvision.nvr.Common.osSelect;
import com.hikvision.nvr.config.Config;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * 海康威视SDK封装类
 */
public class HikvisionSDK {
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
                libPath = System.getProperty("user.dir") + "/lib/libhcnetsdk.so";
            }

            File libFile = new File(libPath);
            if (!libFile.exists()) {
                logger.error("SDK库文件不存在: {}", libPath);
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
                libPath = System.getProperty("user.dir") + "/lib";
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
    public int getLastError() {
        if (hCNetSDK == null) {
            return -1;
        }
        return hCNetSDK.NET_DVR_GetLastError();
    }

    /**
     * 登录设备
     */
    public int login(String ip, short port, String username, String password) {
        if (!initialized || hCNetSDK == null) {
            logger.error("SDK未初始化");
            return -1;
        }

        HCNetSDK.NET_DVR_USER_LOGIN_INFO loginInfo = new HCNetSDK.NET_DVR_USER_LOGIN_INFO();
        HCNetSDK.NET_DVR_DEVICEINFO_V40 deviceInfo = new HCNetSDK.NET_DVR_DEVICEINFO_V40();

        // 设置设备IP地址
        byte[] deviceAddress = new byte[HCNetSDK.NET_DVR_DEV_ADDRESS_MAX_LEN];
        byte[] ipBytes = ip.getBytes();
        System.arraycopy(ipBytes, 0, deviceAddress, 0, Math.min(ipBytes.length, deviceAddress.length - 1));
        loginInfo.sDeviceAddress = deviceAddress;

        // 设置用户名
        byte[] userName = new byte[HCNetSDK.NET_DVR_LOGIN_USERNAME_MAX_LEN];
        byte[] userBytes = username.getBytes();
        System.arraycopy(userBytes, 0, userName, 0, Math.min(userBytes.length, userName.length - 1));
        loginInfo.sUserName = userName;

        // 设置密码
        byte[] passwordBytes = password.getBytes();
        System.arraycopy(passwordBytes, 0, loginInfo.sPassword, 0, Math.min(passwordBytes.length, loginInfo.sPassword.length - 1));

        // 设置端口和登录模式
        loginInfo.wPort = port;
        loginInfo.bUseAsynLogin = false; // 同步登录
        loginInfo.byLoginMode = 0; // 使用SDK私有协议

        // 执行登录
        int userID = hCNetSDK.NET_DVR_Login_V40(loginInfo, deviceInfo);
        if (userID == -1) {
            logger.error("登录失败，错误码: {}", getLastError());
        } else {
            logger.info("设备登录成功: {}:{}", ip, port);
        }

        return userID;
    }

    /**
     * 登出设备
     */
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
